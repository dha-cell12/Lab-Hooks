package com.devicespooflab.hooks.hooks;

import android.os.Build;
import android.os.LocaleList;

import com.devicespooflab.hooks.utils.ConfigManager;

import java.util.Locale;
import java.util.TimeZone;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class LocaleHooks {

    private static final String TAG = "DeviceSpoofLab-Locale";

    public static void hook(LoadPackageParam lpparam) {
        hookTimeZone();
        hookLocale();
        if (Build.VERSION.SDK_INT >= 24) {
            hookLocaleList();
        }
    }

    private static void hookTimeZone() {
        try {
            XposedHelpers.findAndHookMethod(TimeZone.class, "getDefault",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String tz = ConfigManager.getSystemProperty(
                                    "persist.sys.timezone", "America/Los_Angeles");
                            if (tz != null && !tz.isEmpty()) {
                                param.setResult(TimeZone.getTimeZone(tz));
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook TimeZone.getDefault: " + t);
        }
    }

    private static void hookLocale() {
        try {
            XposedHelpers.findAndHookMethod(Locale.class, "getDefault",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(buildLocale());
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook Locale.getDefault: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(Locale.class, "getDefault",
                    Locale.Category.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(buildLocale());
                        }
                    });
        } catch (Throwable t) { /* Category overload is API 24+ */ }
    }

    private static void hookLocaleList() {
        try {
            XposedHelpers.findAndHookMethod(LocaleList.class, "getDefault",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new LocaleList(buildLocale()));
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook LocaleList.getDefault: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(LocaleList.class, "getAdjustedDefault",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new LocaleList(buildLocale()));
                        }
                    });
        } catch (Throwable t) { /* may be missing on some forks */ }
    }

    private static Locale buildLocale() {
        return new Locale(
                ConfigManager.getLocaleLanguage(),
                ConfigManager.getLocaleCountry()
        );
    }
}
