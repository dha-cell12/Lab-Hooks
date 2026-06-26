#include <jni.h>
#include <lsplant.hpp>
#include <android/log.h>
#include "ds_state.h"

#define LOG_TAG "DeviceSpoofLab-JavaHooks"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ds {

// Hooker class name
const char* HOOKER_CLASS = "com/devicespooflab/hooks/ZygiskEntry";

// Global reference to the backup methods
static jobject g_get_serial_backup = nullptr;

// We need a helper object in Java to handle the callback logic if we want to follow LSPlant's standard way
// or we can use the more advanced API if available.
// According to LSPlant README, the callback must be: public Object callback_method(Object[] args)

void InstallJavaHooks(JNIEnv* env) {
    jclass buildClass = env->FindClass("android/os/Build");
    if (!buildClass) {
        LOGE("Could not find android.os.Build");
        return;
    }

    // 1. Direct Field Manipulation via JNI
    std::string model;
    if (LookupProperty("ro.product.model", model)) {
        jfieldID modelField = env->GetStaticFieldID(buildClass, "MODEL", "Ljava/lang/String;");
        if (modelField) {
            env->SetStaticObjectField(buildClass, modelField, env->NewStringUTF(model.c_str()));
            LOGI("Spoofed Build.MODEL via JNI: %s", model.c_str());
        }
    }

    // 2. Build.getSerial() discovery
    jmethodID getSerialMethod = env->GetStaticMethodID(buildClass, "getSerial", "()Ljava/lang/String;");
    if (getSerialMethod) {
        LOGI("LSPlant: Found Build.getSerial(), architecture ready for hook migration");
    }

    // 3. Trigger Java-side initialization
    jclass entryClass = env->FindClass(HOOKER_CLASS);
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
            LOGI("Java ZygiskEntry.init() called");
        }
    }
}

} // namespace ds
