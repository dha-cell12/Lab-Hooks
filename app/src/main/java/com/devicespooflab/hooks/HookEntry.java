package com.devicespooflab.hooks;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import com.devicespooflab.hooks.hooks.AccountHooks;
import com.devicespooflab.hooks.hooks.AdvertisingIdHooks;
import com.devicespooflab.hooks.hooks.AppSetIdHooks;
import com.devicespooflab.hooks.hooks.BatteryHooks;
import com.devicespooflab.hooks.hooks.BuildHooks;
import com.devicespooflab.hooks.hooks.CameraHooks;
import com.devicespooflab.hooks.hooks.DisplayHooks;
import com.devicespooflab.hooks.hooks.EuiccHooks;
import com.devicespooflab.hooks.hooks.HardwareHooks;
import com.devicespooflab.hooks.hooks.InputDeviceHooks;
import com.devicespooflab.hooks.hooks.LocaleHooks;
import com.devicespooflab.hooks.hooks.MediaDrmHooks;
import com.devicespooflab.hooks.hooks.NetworkHooks;
import com.devicespooflab.hooks.hooks.PackageInfoHooks;
import com.devicespooflab.hooks.hooks.PackageManagerHooks;
import com.devicespooflab.hooks.hooks.SensorHooks;
import com.devicespooflab.hooks.hooks.SettingsHooks;
import com.devicespooflab.hooks.hooks.StorageHooks;
import com.devicespooflab.hooks.hooks.SystemPropertiesHooks;
import com.devicespooflab.hooks.hooks.TelephonyHooks;
import com.devicespooflab.hooks.hooks.WebViewHooks;
import com.devicespooflab.hooks.utils.ConfigManager;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * Standalone entry point called from native (java_hooks.cpp InstallJavaHooks)
 * via JNI after Zygisk postAppSpecialize / postServerSpecialize completes.
 *
 * Replaces MainHook.handleLoadPackage from the old LSPosed flow. Builds a
 * LoadPackageParam from ActivityThread static accessors (process name,
 * package name, classloader) instead of receiving one from a hook framework,
 * then runs the same hook installation chain in the same order.
 *
 * Config now comes from the root companion through the native layer
 * (ds::g_props -> NativeHooks.getAllFromNative), so the Application.attach
 * hook + XposedServiceBridge wiring from MainHook is intentionally omitted.
 */
public final class HookEntry {

    private static final String TAG = "DeviceSpoofLab";

    private static volatile boolean sInstalled = false;

    private HookEntry() {}

    public static synchronized void installAll() {
        if (sInstalled) {
            Log.i(TAG, "HookEntry.installAll already ran; skipping");
            return;
        }
        sInstalled = true;

        try {
            ConfigManager.init();
        } catch (Throwable t) {
            Log.e(TAG, "ConfigManager.init failed: " + t.getMessage(), t);
            return;
        }

        // Companion already pushed the profile through native; pull it into
        // the Java config layer so getter methods return spoofed values.
        try {
            boolean loaded = ConfigManager.loadFromRemotePreferences();
            Log.i(TAG, "Loaded profile from native companion=" + loaded
                    + " imei=" + ConfigManager.getIdentifierValue("imei")
                    + " gaid=" + ConfigManager.getIdentifierValue("gaid"));
        } catch (Throwable t) {
            Log.w(TAG, "loadFromRemotePreferences failed: " + t.getMessage());
        }

        LoadPackageParam lpparam = buildLoadPackageParam();
        boolean verbose = ConfigManager.isVerboseLoggingEnabled();
        Log.i(TAG, "installAll start pkg=" + lpparam.packageName
                + " proc=" + lpparam.processName
                + " verbose=" + verbose);

        final boolean isOwnPackage =
                ConfigManager.isOwnPackageProcess(lpparam.processName)
                        || "com.devicespooflab.hooks".equals(lpparam.packageName);
        final int realDeviceSdk = Build.VERSION.SDK_INT;

        // BuildHooks first so direct Build.* reads see spoofed values.
        runHook("BuildHooks", verbose, () -> BuildHooks.hook(lpparam));

        if (!isOwnPackage) {
            runHook("SystemPropertiesHooks", verbose,
                    () -> SystemPropertiesHooks.hook(lpparam));
        } else {
            logSkip(verbose, "SystemPropertiesHooks");
        }

        runHook("HardwareHooks",   verbose, () -> HardwareHooks.hook(lpparam));
        runHook("TelephonyHooks",  verbose, () -> TelephonyHooks.hook(lpparam));
        runHook("SettingsHooks",   verbose, () -> SettingsHooks.hook(lpparam));
        runHook("AdvertisingIdHooks", verbose, () -> AdvertisingIdHooks.hook(lpparam));

        if (realDeviceSdk >= 30) {
            runHook("AppSetIdHooks", verbose,
                    () -> AppSetIdHooks.hook(lpparam, realDeviceSdk));
        }

        runHook("MediaDrmHooks",        verbose, () -> MediaDrmHooks.hook(lpparam));
        runHook("WebViewHooks",         verbose, () -> WebViewHooks.hook(lpparam));
        runHook("PackageManagerHooks",  verbose, () -> PackageManagerHooks.hook(lpparam));
        runHook("NetworkHooks",         verbose, () -> NetworkHooks.hook(lpparam));

        if (!isOwnPackage) {
            runHook("DisplayHooks", verbose, () -> DisplayHooks.hook(lpparam));
        } else {
            logSkip(verbose, "DisplayHooks");
        }

        runHook("SensorHooks",  verbose, () -> SensorHooks.hook(lpparam));
        runHook("CameraHooks",  verbose, () -> CameraHooks.hook(lpparam));
        runHook("StorageHooks", verbose, () -> StorageHooks.hook(lpparam));

        if (!isOwnPackage) {
            runHook("LocaleHooks", verbose, () -> LocaleHooks.hook(lpparam));
        } else {
            logSkip(verbose, "LocaleHooks");
        }

        if (ConfigManager.isHideAccountsEnabled()) {
            runHook("AccountHooks", verbose, () -> AccountHooks.hook(lpparam));
        } else if (verbose) {
            Log.i(TAG, "AccountHooks skipped (hooks.hide_accounts=0)");
        }

        runHook("PackageInfoHooks", verbose, () -> PackageInfoHooks.hook(lpparam));
        runHook("BatteryHooks",     verbose, () -> BatteryHooks.hook(lpparam));

        if (realDeviceSdk >= 28) {
            runHook("EuiccHooks", verbose,
                    () -> EuiccHooks.hook(lpparam, realDeviceSdk));
        }

        runHook("InputDeviceHooks", verbose, () -> InputDeviceHooks.hook(lpparam));

        if (!isOwnPackage) {
            try {
                boolean ok = NativeHooks.tryInstall(ConfigManager.getAllSpoofedProperties());
                if (verbose) {
                    Log.i(TAG, ok
                            ? "NativeHooks loaded"
                            : "NativeHooks unavailable (Java-only spoofing active)");
                }
            } catch (Throwable t) {
                Log.w(TAG, "NativeHooks failed: " + t.getMessage());
            }
        } else {
            logSkip(verbose, "NativeHooks");
        }

        if (verbose) {
            Log.i(TAG, "All hooks initialized for " + lpparam.packageName);
        }
    }

    private interface HookTask {
        void run() throws Throwable;
    }

    private static void runHook(String name, boolean verbose, HookTask task) {
        try {
            task.run();
            if (verbose) {
                Log.i(TAG, name + " loaded");
            }
        } catch (Throwable t) {
            Log.w(TAG, name + " failed: " + t.getMessage(), t);
        }
    }

    private static void logSkip(boolean verbose, String name) {
        if (verbose) {
            Log.i(TAG, name + " skipped for module process");
        }
    }

    /**
     * Build the LoadPackageParam from runtime state. Runs inside the target
     * process at postAppSpecialize/postServerSpecialize time, so ActivityThread
     * is initialized but Application.attach may or may not have completed.
     * appInfo can therefore be null; callers (PackageInfoHooks) already handle
     * that case.
     */
    private static LoadPackageParam buildLoadPackageParam() {
        LoadPackageParam lp = new LoadPackageParam();
        lp.classLoader = Thread.currentThread().getContextClassLoader();
        lp.packageName = readActivityThreadString("currentPackageName");
        lp.processName = readActivityThreadString("currentProcessName");
        if (lp.packageName == null) {
            // system_server has no package; fall back to processName so the
            // own-package guard below still does something sensible.
            lp.packageName = lp.processName != null ? lp.processName : "";
        }
        if (lp.processName == null) {
            lp.processName = lp.packageName;
        }
        lp.appInfo = readApplicationInfo();
        return lp;
    }

    private static String readActivityThreadString(String methodName) {
        try {
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Method m = atCls.getMethod(methodName);
            Object value = m.invoke(null);
            return value instanceof String ? (String) value : null;
        } catch (Throwable t) {
            Log.w(TAG, "ActivityThread." + methodName + "() failed: " + t.getMessage());
            return null;
        }
    }

    private static ApplicationInfo readApplicationInfo() {
        try {
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Object thread = atCls.getMethod("currentActivityThread").invoke(null);
            if (thread == null) return null;
            Object app = atCls.getMethod("getApplication").invoke(thread);
            if (app instanceof Application) {
                return ((Application) app).getApplicationInfo();
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
