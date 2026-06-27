package com.devicespooflab.hooks.hooks;

import android.os.Build;

import com.devicespooflab.hooks.utils.ConfigManager;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class EuiccHooks {

    private static final String TAG = "DeviceSpoofLab-Euicc";

    public static void hook(LoadPackageParam lpparam) {
        hook(lpparam, Build.VERSION.SDK_INT);
    }

    public static void hook(LoadPackageParam lpparam, int realDeviceSdk) {
        if (realDeviceSdk < 28) return;

        Class<?> em = XposedHelpers.findClassIfExists(
                "android.telephony.euicc.EuiccManager", lpparam.classLoader);
        if (em == null) return;

        try {
            XposedHelpers.findAndHookMethod(em, "getEid",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getEid();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook EuiccManager.getEid: " + t);
        }
    }
}
