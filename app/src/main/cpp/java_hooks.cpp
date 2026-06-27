#include "ds_state.h"

#include <jni.h>
#include <lsplant.hpp>

// Java-side method hooking via LSPlant (hướng A: hooks are declared in Java,
// LSPlant performs the actual installation).
//
// The Java shim (XposedHelpers.findAndHookMethod) builds an LspHooker, then
// calls nativeHookMethod() below to install the hook through lsplant::Hook.
// The backup (original) Method returned by LSPlant is handed back to Java so
// LspHooker.callback can invoke the original.

namespace ds {

void InstallJavaHooks(JNIEnv* env) {
    if (env == nullptr) {
        DS_LOGW("InstallJavaHooks: null env; skipping");
        return;
    }
    // With hướng A the hook list lives in Java; each hooks/ class installs its
    // own hooks via the XposedHelpers shim -> nativeHookMethod. Nothing to do
    // here beyond confirming LSPlant is ready.
    DS_LOGI("InstallJavaHooks: LSPlant ready; Java shim installs hooks on demand");
}

}  // namespace ds

// Installs a single hook through LSPlant. Called from the Java shim.
//   target   - java.lang.reflect.Method/Constructor to hook
//   hooker   - LspHooker instance (the context object)
//   callback - Method object pointing at LspHooker.callback(Object[])
// Returns the backup Method (to invoke the original), or null on failure.
extern "C" JNIEXPORT jobject JNICALL
Java_com_devicespooflab_hooks_xposed_XposedBridge_nativeHookMethod(
        JNIEnv* env, jclass /*cls*/, jobject target, jobject hooker,
        jobject callback) {
    if (target == nullptr || hooker == nullptr || callback == nullptr) {
        DS_LOGW("nativeHookMethod: null argument");
        return nullptr;
    }
    jobject backup = lsplant::Hook(env, target, hooker, callback);
    if (backup == nullptr) {
        DS_LOGW("nativeHookMethod: lsplant::Hook returned null");
        return nullptr;
    }
    return backup;
}
