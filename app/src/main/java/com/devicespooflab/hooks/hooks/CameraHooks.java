package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class CameraHooks {

    private static final String TAG = "DeviceSpoofLab-Camera";

    public static void hook(LoadPackageParam lpparam) {
        Class<?> cm = XposedHelpers.findClassIfExists(
                "android.hardware.camera2.CameraManager", lpparam.classLoader);
        if (cm == null) return;

        try {
            XposedHelpers.findAndHookMethod(cm, "getCameraIdList",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String[] ids = (String[]) param.getResult();
                            if (ids == null) return;
                            int kept = 0;
                            for (String id : ids) {
                                if (id != null && !id.toLowerCase().contains("emulator")) {
                                    ids[kept++] = id;
                                }
                            }
                            if (kept != ids.length) {
                                String[] trimmed = new String[kept];
                                System.arraycopy(ids, 0, trimmed, 0, kept);
                                param.setResult(trimmed);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook getCameraIdList: " + t);
        }
    }
}
