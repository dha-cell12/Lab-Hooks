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
    // Bridge C++ -> Java: invoke HookEntry.installAll(), which iterates the
    // hooks/ classes and installs each one via the XposedHelpers shim ->
    // nativeHookMethod -> lsplant::Hook.
    jclass entryClass = env->FindClass("com/devicespooflab/hooks/HookEntry");
    if (entryClass == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        DS_LOGW("InstallJavaHooks: HookEntry class not found; Java hooks skipped");
        return;
    }
    jmethodID installAll = env->GetStaticMethodID(entryClass, "installAll", "()V");
    if (installAll == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(entryClass);
        DS_LOGW("InstallJavaHooks: HookEntry.installAll() not found; Java hooks skipped");
        return;
    }
    env->CallStaticVoidMethod(entryClass, installAll);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        DS_LOGW("InstallJavaHooks: HookEntry.installAll() threw; some Java hooks may be missing");
    } else {
        DS_LOGI("InstallJavaHooks: HookEntry.installAll() completed");
    }
    env->DeleteLocalRef(entryClass);
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
