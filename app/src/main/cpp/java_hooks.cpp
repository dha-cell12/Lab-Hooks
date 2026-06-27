#include "ds_state.h"

#include <jni.h>

// Java-side method hooking via LSPlant.
//
// Phase 1 placeholder: InitLSPlant is wired up and called from the Zygisk
// entry, but the actual method hooks (the Xposed-shim layer translating
// findAndHookMethod/XC_MethodHook semantics onto lsplant::Hook) are installed
// here in phase 2. For now this is a logging stub so the initialization path
// compiles and runs end-to-end.

namespace ds {

void InstallJavaHooks(JNIEnv* env) {
    if (env == nullptr) {
        DS_LOGW("InstallJavaHooks: null env; skipping");
        return;
    }
    DS_LOGI("InstallJavaHooks: LSPlant ready; Java hook shim not yet installed "
            "(phase 2)");
}

}  // namespace ds
