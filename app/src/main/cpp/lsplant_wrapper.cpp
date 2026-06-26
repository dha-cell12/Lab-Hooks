#include <jni.h>
#include <lsplant.hpp>
#include <android/log.h>
#include <unordered_map>
#include <mutex>
#include "ds_state.h"

#define LOG_TAG "DeviceSpoofLab-LSPlantWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    struct HookInfo {
        jobject target;
        jobject backup;
    };
    std::unordered_map<jmethodID, jobject> g_backups;
    std::mutex g_hook_mutex;

    jclass g_wrapper_class = nullptr;
    jmethodID g_entry_point_mid = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_devicespooflab_hooks_LSPlantJavaWrapper_nativeHook(JNIEnv* env, jclass clazz, jobject method) {
    std::lock_guard<std::mutex> lock(g_hook_mutex);

    // Find the callback method on the wrapper class
    if (!g_wrapper_class) {
        g_wrapper_class = (jclass)env->NewGlobalRef(clazz);
        g_entry_point_mid = env->GetStaticMethodID(g_wrapper_class, "entryPoint",
            "(Ljava/lang/reflect/Member;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    }

    // Prepare for LSPlant hook
    jobject target = env->NewGlobalRef(method);

    // We need a static callback that matches LSPlant signature or a method on an object.
    // LSPlant's Hook takes: (env, target_method, hooker_object, callback_method)
    // Here we use the wrapper class as the hooker and entryPoint as the callback.

    jobject backup = lsplant::Hook(env, target, nullptr, env->ToReflectedMethod(g_wrapper_class, g_entry_point_mid, JNI_FALSE));

    if (backup) {
        jmethodID targetMID = env->FromReflectedMethod(method);
        g_backups[targetMID] = env->NewGlobalRef(backup);
        LOGI("Successfully hooked method via LSPlant");
    } else {
        LOGE("Failed to hook method via LSPlant");
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_devicespooflab_hooks_LSPlantJavaWrapper_callOriginal(JNIEnv* env, jclass /*clazz*/, jobject method, jobject thisObject, jobjectArray args) {
    jmethodID targetMID = env->FromReflectedMethod(method);
    jobject backup = nullptr;

    {
        std::lock_guard<std::mutex> lock(g_hook_mutex);
        auto it = g_backups.find(targetMID);
        if (it != g_backups.end()) {
            backup = it->second;
        }
    }

    if (!backup) {
        LOGE("No backup found for callOriginal");
        return nullptr;
    }

    // Call the backup method using reflection (LSPlant returns a Method object)
    jclass methodClass = env->FindClass("java/lang/reflect/Method");
    jmethodID invokeMID = env->GetMethodID(methodClass, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

    return env->CallObjectMethod(backup, invokeMID, thisObject, args);
}
