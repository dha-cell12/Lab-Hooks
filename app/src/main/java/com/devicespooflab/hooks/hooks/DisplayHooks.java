package com.devicespooflab.hooks.hooks;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Display;

import com.devicespooflab.hooks.utils.ConfigManager;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class DisplayHooks {

    private static final String TAG = "DeviceSpoofLab-Display";
    private static final float REFRESH_RATE_HZ = 120.0f;

    public static void hook(LoadPackageParam lpparam) {
        try {
            hookDisplayMethods();
            hookResourcesGetDisplayMetrics();
            hookWindowMetrics(lpparam);
            hookRefreshRate();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": init failed: " + t);
        }
    }

    private static void hookDisplayMethods() {
        try {
            XposedHelpers.findAndHookMethod(Display.class, "getRealMetrics",
                    DisplayMetrics.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            DisplayMetrics dm = (DisplayMetrics) param.args[0];
                            applySpoofedMetrics(dm);
                        }
                    });
        } catch (Throwable t) { logFail("Display.getRealMetrics", t); }

        try {
            XposedHelpers.findAndHookMethod(Display.class, "getMetrics",
                    DisplayMetrics.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            DisplayMetrics dm = (DisplayMetrics) param.args[0];
                            applySpoofedMetrics(dm);
                        }
                    });
        } catch (Throwable t) { logFail("Display.getMetrics", t); }

        try {
            XposedHelpers.findAndHookMethod(Display.class, "getRealSize",
                    Point.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Point p = (Point) param.args[0];
                            if (p != null) {
                                p.x = ConfigManager.getScreenWidth();
                                p.y = ConfigManager.getScreenHeight();
                            }
                        }
                    });
        } catch (Throwable t) { logFail("Display.getRealSize", t); }

        try {
            XposedHelpers.findAndHookMethod(Display.class, "getSize",
                    Point.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Point p = (Point) param.args[0];
                            if (p != null) {
                                p.x = ConfigManager.getScreenWidth();
                                p.y = ConfigManager.getScreenHeight();
                            }
                        }
                    });
        } catch (Throwable t) { logFail("Display.getSize", t); }

        try {
            XposedHelpers.findAndHookMethod(Display.class, "getWidth",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getScreenWidth());
                        }
                    });
        } catch (Throwable t) { /* deprecated method may be missing */ }

        try {
            XposedHelpers.findAndHookMethod(Display.class, "getHeight",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getScreenHeight());
                        }
                    });
        } catch (Throwable t) { /* deprecated method may be missing */ }
    }

    private static void hookResourcesGetDisplayMetrics() {
        try {
            XposedHelpers.findAndHookMethod(android.content.res.Resources.class,
                    "getDisplayMetrics",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            DisplayMetrics dm = (DisplayMetrics) param.getResult();
                            applySpoofedMetrics(dm);
                        }
                    });
        } catch (Throwable t) { logFail("Resources.getDisplayMetrics", t); }
    }

    private static void hookRefreshRate() {
        try {
            XposedHelpers.findAndHookMethod(Display.class, "getRefreshRate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(REFRESH_RATE_HZ);
                        }
                    });
        } catch (Throwable t) { logFail("Display.getRefreshRate", t); }
    }

    private static void hookWindowMetrics(LoadPackageParam lpparam) {
        // Android 11+: WindowMetrics.getBounds() returns a Rect with the display
        // bounds. Apps using this code path bypass the legacy Display getters.
        Class<?> wmClass = XposedHelpers.findClassIfExists(
                "android.view.WindowMetrics", lpparam.classLoader);
        if (wmClass == null) return;

        try {
            XposedHelpers.findAndHookMethod(wmClass, "getBounds",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new Rect(0, 0,
                                    ConfigManager.getScreenWidth(),
                                    ConfigManager.getScreenHeight()));
                        }
                    });
        } catch (Throwable t) { logFail("WindowMetrics.getBounds", t); }
    }

    private static void applySpoofedMetrics(DisplayMetrics dm) {
        if (dm == null) return;
        int w = ConfigManager.getScreenWidth();
        int h = ConfigManager.getScreenHeight();
        int densityDpi = ConfigManager.getScreenDensity();
        float density = densityDpi / 160.0f;
        float fontScale = dm.density > 0.0f ? dm.scaledDensity / dm.density : 1.0f;
        if (fontScale <= 0.0f || Float.isNaN(fontScale) || Float.isInfinite(fontScale)) {
            fontScale = 1.0f;
        }

        dm.widthPixels = w;
        dm.heightPixels = h;
        dm.densityDpi = densityDpi;
        dm.density = density;
        dm.scaledDensity = density * fontScale;
        dm.xdpi = densityDpi;
        dm.ydpi = densityDpi;
    }

    private static void logFail(String what, Throwable t) {
        XposedBridge.log(TAG + ": failed to hook " + what + ": " + t);
    }
}
