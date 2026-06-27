#include "ds_state.h"
#include "zygisk.hpp"
#include "companion_protocol.h"
#include "companion_io.h"

#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

namespace {

// ---- Scope handling (companion-sourced, cached by the module per process) ----

// The package name of our own module app; never spoof its own process.
constexpr const char* kOwnPackage = "com.devicespooflab.hooks";

// Read a length-prefixed line list from a fd opened on scope.list. The
// companion sends scope entries through RequestPairs (key = package, empty
// value), so a process-local set is built from that.
bool LoadScopeFromCompanion(int companionFd,
                            std::unordered_set<std::string>& out) {
    std::unordered_map<std::string, std::string> pairs;
    if (!ds::RequestPairs(companionFd, ds::REQ_SCOPE, pairs)) {
        return false;
    }
    for (auto& kv : pairs) {
        if (!kv.first.empty()) out.insert(kv.first);
    }
    return true;
}

// ---- Module ----

class DeviceSpoofModule : public zygisk::ModuleBase {
public:
    void onLoad(Api* api, JNIEnv* env) override {
        api_ = api;
        env_ = env;
        // JNIEnv is thread-bound; the JavaVM is process-stable. Cache the VM so
        // we can fetch the calling thread's JNIEnv at hook time instead of
        // reusing this (possibly stale, wrong-thread) env_.
        if (env != nullptr) env->GetJavaVM(&vm_);
    }

    void preAppSpecialize(AppSpecializeArgs* args) override {
        // Reset per-process state (the module object is static and reused
        // across forks within the same zygote).
        should_spoof_ = false;
        profile_.clear();

        if (args == nullptr || args->nice_name == nullptr) {
            dontUnload();
            return;
        }

        const char* niceName =
            env_->GetStringUTFChars(args->nice_name, nullptr);
        if (niceName == nullptr) {
            dontUnload();
            return;
        }
        std::string packageName(niceName);
        env_->ReleaseStringUTFChars(args->nice_name, niceName);

        // Never spoof our own UI/editor process.
        if (packageName == kOwnPackage) {
            cleanProcess();
            return;
        }

        // connectCompanion only works in pre-specialize (still zygote priv).
        int companionFd = api_->connectCompanion();
        if (companionFd < 0) {
            cleanProcess();
            return;
        }

        std::unordered_set<std::string> scope;
        bool scopeOk = LoadScopeFromCompanion(companionFd, scope);
        if (!scopeOk || scope.find(packageName) == scope.end()) {
            close(companionFd);
            cleanProcess();
            return;
        }

        // In scope: pull the full profile now, while we still can talk to the
        // companion. Apply it in postAppSpecialize once the process is live.
        should_spoof_ = ds::RequestPairs(companionFd, ds::REQ_PROFILE, profile_);
        close(companionFd);

        if (!should_spoof_) {
            cleanProcess();
            return;
        }
        // Keep the library mapped; we hook in postAppSpecialize.
        dontUnload();
    }

    void postAppSpecialize(const AppSpecializeArgs*) override {
        if (!should_spoof_) return;
        applyProfileAndHook();
    }

    void preServerSpecialize(ServerSpecializeArgs*) override {
        // system_server: pull the profile so native property hooks (uname,
        // __system_property_*) cover the system process too.
        profile_.clear();
        should_spoof_ = false;

        int companionFd = api_->connectCompanion();
        if (companionFd < 0) {
            dontUnload();
            return;
        }
        should_spoof_ = ds::RequestPairs(companionFd, ds::REQ_PROFILE, profile_);
        close(companionFd);
        dontUnload();
    }

    void postServerSpecialize(const ServerSpecializeArgs*) override {
        if (!should_spoof_) return;
        applyProfileAndHook();
    }

private:
    Api* api_ = nullptr;
    JNIEnv* env_ = nullptr;
    JavaVM* vm_ = nullptr;
    bool should_spoof_ = false;
    std::unordered_map<std::string, std::string> profile_;

    void dontUnload() {
        // Default: keep the library; nothing to do. Present for symmetry.
    }

    void cleanProcess() {
        // Not in scope: drop all of our files from this process's mount
        // namespace and unload the library so we leave no trace.
        api_->setOption(zygisk::FORCE_DENYLIST_UNMOUNT);
        api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

    void applyProfileAndHook() {
        if (profile_.empty()) return;
        // Hand the profile to the existing native layer and install the PLT
        // hooks that were previously driven through JNI nativeInstall().
        ds::g_props = std::move(profile_);
        ds::InstallPropertyHooks();

        // Initialize LSPlant for Java method hooking. JNIEnv is thread-bound,
        // so fetch the calling thread's env from the process-stable JavaVM
        // rather than reusing the cached onLoad env_.
        if (vm_ != nullptr) {
            JNIEnv* env = nullptr;
            if (vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6)
                    == JNI_OK && env != nullptr) {
                if (ds::InitLSPlant(env)) {
                    ds::InstallJavaHooks(env);
                }
            } else {
                DS_LOGW("applyProfileAndHook: no JNIEnv for current thread");
            }
        } else {
            DS_LOGW("applyProfileAndHook: JavaVM unavailable; Java hooks skipped");
        }
    }
};

}  // namespace

REGISTER_ZYGISK_MODULE(DeviceSpoofModule)
