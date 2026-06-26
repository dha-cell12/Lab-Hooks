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
    std::unordered_map<jmethodID, jobject> g_backups;
    std::mutex g_hook_mutex;
}

extern "C" JNIEXPORT void JNICALL
Java_com_devicespooflab_hooks_LSPlantJavaWrapper_nativeHook(JNIEnv* env, jclass /*clazz*/, jobject method, jobject hooker, jobject callback) {
    std::lock_guard<std::mutex> lock(g_hook_mutex);

    jmethodID targetMID = env->FromReflectedMethod(method);
    if (g_backups.find(targetMID) != g_backups.end()) {
        return; // Already hooked
    }

    jobject target = env->NewGlobalRef(method);
    jobject backup = lsplant::Hook(env, target, hooker, callback);

    if (backup) {
        g_backups[targetMID] = env->NewGlobalRef(backup);
        LOGI("Successfully hooked method via LSPlant");
    } else {
        LOGE("Failed to hook method via LSPlant");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_devicespooflab_hooks_LSPlantJavaWrapper_nativeUnhook(JNIEnv* env, jclass /*clazz*/, jobject method) {
    std::lock_guard<std::mutex> lock(g_hook_mutex);

    jmethodID targetMID = env->FromReflectedMethod(method);
    auto it = g_backups.find(targetMID);
    if (it != g_backups.end()) {
        if (lsplant::UnHook(env, method)) {
            env->DeleteGlobalRef(it->second);
            g_backups.erase(it);
            LOGI("Successfully unhooked method via LSPlant");
        } else {
            LOGE("Failed to unhook method via LSPlant");
        }
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

    jclass methodClass = env->FindClass("java/lang/reflect/Method");
    jmethodID invokeMID = env->GetMethodID(methodClass, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

    return env->CallObjectMethod(backup, invokeMID, thisObject, args);
}
