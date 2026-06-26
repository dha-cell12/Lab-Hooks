package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.os.Build;
import android.os.LocaleList;

import com.devicespooflab.hooks.utils.ConfigManager;

import java.util.Locale;
import java.util.TimeZone;






public class LocaleHooks {

    private static final String TAG = "DeviceSpoofLab-Locale";

    public static void hook(ClassLoader classLoader) {
        hookTimeZone();
        hookLocale();
        if (Build.VERSION.SDK_INT >= 24) {
            hookLocaleList();
        }
    }

    private static void hookTimeZone() {
        try {
            LSPlantJavaWrapper.findAndHookMethod(TimeZone.class, "getDefault",
                    new ZygiskMethodHook() {
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
            android.util.Log.i(TAG + ": failed to hook TimeZone.getDefault: " + t);
        }
    }

    private static void hookLocale() {
        try {
            LSPlantJavaWrapper.findAndHookMethod(Locale.class, "getDefault",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(buildLocale());
                        }
                    });
        } catch (Throwable t) {
            android.util.Log.i(TAG + ": failed to hook Locale.getDefault: " + t);
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(Locale.class, "getDefault",
                    Locale.Category.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(buildLocale());
                        }
                    });
        } catch (Throwable t) { /* Category overload is API 24+ */ }
    }

    private static void hookLocaleList() {
        try {
            LSPlantJavaWrapper.findAndHookMethod(LocaleList.class, "getDefault",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new LocaleList(buildLocale()));
                        }
                    });
        } catch (Throwable t) {
            android.util.Log.i(TAG + ": failed to hook LocaleList.getDefault: " + t);
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(LocaleList.class, "getAdjustedDefault",
                    new ZygiskMethodHook() {
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


    private static Class<?> findClass(String name, ClassLoader loader) { try { return Class.forName(name, true, loader); } catch (Exception e) { return null; } }
}
