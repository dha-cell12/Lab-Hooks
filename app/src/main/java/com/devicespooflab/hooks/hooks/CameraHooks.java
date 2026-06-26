package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;






public class CameraHooks {

    private static final String TAG = "DeviceSpoofLab-Camera";

    public static void hook(ClassLoader classLoader, String processName) {
        Class<?> cm = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.hardware.camera2.CameraManager", classLoader);
        if (cm == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(cm, "getCameraIdList",
                    new ZygiskMethodHook() {
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
            android.util.Log.i(TAG + ": failed to hook getCameraIdList: " + t);
        }
    }



}
