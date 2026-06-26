package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.view.InputDevice;






public class InputDeviceHooks {

    private static final String TAG = "DeviceSpoofLab-InputDevice";

    public static void hook(ClassLoader classLoader, String processName) {
        try {
            LSPlantJavaWrapper.findAndHookMethod(InputDevice.class, "getName",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = (String) param.getResult();
                            if (name != null && isEmulator(name)) {
                                param.setResult("Touch");
                            }
                        }
                    });
        } catch (Throwable t) {
            android.util.Log.i(TAG + ": failed to hook InputDevice.getName: " + t);
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(InputDevice.class, "getDescriptor",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String desc = (String) param.getResult();
                            if (desc != null && isEmulator(desc)) {
                                param.setResult("0");
                            }
                        }
                    });
        } catch (Throwable t) { /* getDescriptor signature stable */ }
    }

    private static boolean isEmulator(String s) {
        String lower = s.toLowerCase();
        return lower.contains("goldfish") || lower.contains("qemu")
                || lower.contains("ranchu") || lower.contains("vbox");
    }



}
