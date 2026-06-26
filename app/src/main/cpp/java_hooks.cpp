#include <jni.h>
#include <lsplant.hpp>
#include <android/log.h>
#include "ds_state.h"

#define LOG_TAG "DeviceSpoofLab-JavaHooks"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace ds {

void InstallJavaHooks(JNIEnv* env) {
    // Basic JNI-based spoofing for static fields (most efficient for these)
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

    // Note: Complex method hooking is now handled on the Java side
    // via ZygiskEntry.init() and LSPlantJavaWrapper.
}

} // namespace ds
