package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.os.Build;

import com.devicespooflab.hooks.utils.ConfigManager;






public class EuiccHooks {

    private static final String TAG = "DeviceSpoofLab-Euicc";

    public static void hook(ClassLoader classLoader, String processName) {
        hook(lpparam, Build.VERSION.SDK_INT);
    }

    public static void hook(ClassLoader classLoader, int realDeviceSdk) {
        if (realDeviceSdk < 28) return;

        Class<?> em = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.telephony.euicc.EuiccManager", classLoader);
        if (em == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(em, "getEid",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getEid();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (Throwable t) {
            android.util.Log.i(TAG + ": failed to hook EuiccManager.getEid: " + t);
        }
    }



}
