package com.devicespooflab.hooks.hooks;

import android.view.InputDevice;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class InputDeviceHooks {

    private static final String TAG = "DeviceSpoofLab-InputDevice";

    public static void hook(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(InputDevice.class, "getName",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String name = (String) param.getResult();
                            if (name != null && isEmulator(name)) {
                                param.setResult("Touch");
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook InputDevice.getName: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(InputDevice.class, "getDescriptor",
                    new XC_MethodHook() {
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
