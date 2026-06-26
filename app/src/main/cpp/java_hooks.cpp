#include <jni.h>
#include <lsplant.hpp>
#include <android/log.h>
#include "ds_state.h"

#define LOG_TAG "DeviceSpoofLab-JavaHooks"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace ds {

void InstallJavaHooks(JNIEnv* env) {
    jclass buildClass = env->FindClass("android/os/Build");
    if (buildClass) {
        std::string model;
        if (LookupProperty("ro.product.model", model)) {
            jfieldID modelField = env->GetStaticFieldID(buildClass, "MODEL", "Ljava/lang/String;");
            if (modelField) {
                env->SetStaticObjectField(buildClass, modelField, env->NewStringUTF(model.c_str()));
            }
        }
    }

    // Trigger Java ZygiskEntry.init()
    jclass entryClass = env->FindClass("com/devicespooflab/hooks/ZygiskEntry");
    if (entryClass) {
        jmethodID initMID = env->GetStaticMethodID(entryClass, "init", "(Ljava/lang/ClassLoader;)V");
        if (initMID) {
            // Get current class loader
            jclass threadClass = env->FindClass("java/lang/Thread");
            jmethodID currentThreadMID = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
            jobject currentThread = env->CallStaticObjectMethod(threadClass, currentThreadMID);
            jmethodID getContextClassLoaderMID = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
            jobject classLoader = env->CallObjectMethod(currentThread, getContextClassLoaderMID);

            env->CallStaticVoidMethod(entryClass, initMID, classLoader);
            LOGI("Java ZygiskEntry.init() triggered for all hooks");
        }
    }
}

} // namespace ds
