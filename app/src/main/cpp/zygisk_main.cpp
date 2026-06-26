#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sstream>
#include "include/zygisk.hpp"
#include "ds_state.h"
#include <lsplant.hpp>

#define LOG_TAG "DeviceSpoofLab-Zygisk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

namespace {
    jobject CreateJavaMap(JNIEnv* env, const std::unordered_map<std::string, std::string>& map) {
        jclass mapClass = env->FindClass("java/util/HashMap");
        jmethodID init = env->GetMethodID(mapClass, "<init>", "(I)V");
        jobject hashMap = env->NewObject(mapClass, init, (jint)map.size());
        jmethodID put = env->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

        for (const auto& it : map) {
            jstring key = env->NewStringUTF(it.first.c_str());
            jstring val = env->NewStringUTF(it.second.c_str());
            env->CallObjectMethod(hashMap, put, key, val);
            env->DeleteLocalRef(key);
            env->DeleteLocalRef(val);
        }
        return hashMap;
    }
}

static void companion_handler(int socket) {
    int fd = open("/data/adb/devicespooflab/config.prop", O_RDONLY);
    if (fd < 0) {
        off_t error_size = 0;
        write(socket, &error_size, sizeof(error_size));
        return;
    }

    off_t size = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);
    write(socket, &size, sizeof(size));

    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        write(socket, buf, n);
    }
    close(fd);
}

REGISTER_ZYGISK_COMPANION(companion_handler)

class DeviceSpoofModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        const char *nice_name = env->GetStringUTFChars(args->nice_name, nullptr);
        if (nice_name) {
            std::string process_name(nice_name);
            env->ReleaseStringUTFChars(args->nice_name, nice_name);

            if (process_name == "com.devicespooflab.hooks") {
                api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
                return;
            }

            enable_hook = true;

            int socket = api->connectCompanion();
            if (socket >= 0) {
                off_t size;
                if (read(socket, &size, sizeof(size)) == sizeof(size) && size > 0) {
                    std::string content;
                    content.resize(size);
                    read(socket, &content[0], size);

                    std::stringstream ss(content);
                    std::string line;
                    while (std::getline(ss, line)) {
                        if (line.empty() || line[0] == '#') continue;
                        size_t pos = line.find('=');
                        if (pos != std::string::npos) {
                            ds::g_props[line.substr(0, pos)] = line.substr(pos + 1);
                        }
                    }
                }
                close(socket);
            }
        }
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        if (!enable_hook) return;

        lsplant::InitInfo info;
        if (!lsplant::Init(env, info)) {
            LOGE("LSPlant::Init failed");
            return;
        }

        // Pass config to Java
        if (!ds::g_props.empty()) {
            jclass entryClass = env->FindClass("com/devicespooflab/hooks/ZygiskEntry");
            if (entryClass) {
                jmethodID setConfigMID = env->GetStaticMethodID(entryClass, "setConfig", "(Ljava/util/Map;)V");
                if (setConfigMID) {
                    jobject javaMap = CreateJavaMap(env, ds::g_props);
                    env->CallStaticVoidMethod(entryClass, setConfigMID, javaMap);
                }

                // Initialize Java hooks
                jmethodID initMID = env->GetStaticMethodID(entryClass, "init", "(Ljava/lang/ClassLoader;)V");
                if (initMID) {
                    env->CallStaticVoidMethod(entryClass, initMID, nullptr);
                }
            }

            // Also apply native hooks
            ds::InstallPropertyHooks();
        }
    }

private:
    Api *api;
    JNIEnv *env;
    bool enable_hook = false;
};

REGISTER_ZYGISK_MODULE(DeviceSpoofModule)
