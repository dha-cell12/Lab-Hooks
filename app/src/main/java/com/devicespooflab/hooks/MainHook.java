package com.devicespooflab.hooks;

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
import com.devicespooflab.hooks.utils.XposedServiceBridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "DeviceSpoofLab";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            ConfigManager.init();
        } catch (Exception e) {
            Log.e(TAG, "Failed to init config: " + e.getMessage(), e);
            XposedBridge.log(TAG + ": Failed to init config: " + e.getMessage());
            return;
        }

        boolean verbose = ConfigManager.isVerboseLoggingEnabled();
        Log.i(TAG, "handleLoadPackage start pkg=" + lpparam.packageName
                + " verbose=" + verbose
                + " imei=" + ConfigManager.getIdentifierValue("imei")
                + " gaid=" + ConfigManager.getIdentifierValue("gaid"));
        logInfo(verbose, TAG + ": Loading hooks for " + lpparam.packageName);
        final int realDeviceSdk = Build.VERSION.SDK_INT;

        // XposedServiceHelper has independent static state in the app loader vs
        // the module loader. Defer init until Application.attach so the bridge
        // can bind through the app loader and see the same mListener that
        // XposedProvider notifies on SEND_BINDER.
        final boolean isOwnPackage = ConfigManager.isOwnPackageProcess(lpparam.processName)
                || "com.devicespooflab.hooks".equals(lpparam.packageName);

        final Runnable onBinderReady = new Runnable() {
            @Override
            public void run() {
                if (isOwnPackage) {
                    // Fallback only: with the module out of its own scope this
                    // branch never runs (MainActivity publishes directly). It
                    // survives for users who re-scope the module to itself, e.g.
                    // on stock LSPosed. The new-API RemotePreferences is
                    // read-only, so publishing needs the writable SEND_BINDER.
                    if (!XposedServiceBridge.isServiceWritable()) return;
                    ConfigManager.publishToRemotePreferences();
                } else {
                    boolean loaded = ConfigManager.loadFromRemotePreferences();
                    if (loaded) {
                        BuildHooks.refreshStaticFields(lpparam.classLoader);
                    }
                    // Live-update at runtime is intentionally not supported:
                    // the companion can only be reached from pre-specialize, so
                    // config edits take effect on the target's next launch.
                    // See docs/live-update-design.md (Huớng E) for the
                    // restart-free approach deferred for later.
                    Log.i(TAG, "Companion profile load=" + loaded
                            + " imei=" + ConfigManager.getIdentifierValue("imei")
                            + " gaid=" + ConfigManager.getIdentifierValue("gaid"));
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod("android.app.Application",
                    lpparam.classLoader, "attach",
                    android.content.Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                android.content.Context ctx =
                                        (android.content.Context) param.args[0];
                                XposedServiceBridge.init(ctx, onBinderReady);
                                // The own UI process is no longer in scope, so it
                                // never reaches here; it publishes directly from
                                // MainActivity via the binder delivered to
                                // XposedProvider. This path now only serves target
                                // apps: seed from RemotePreferences when the new-API
                                // channel is up but config is still empty.
                                if (!isOwnPackage
                                        && ConfigManager.getIdentifierValue("imei").isEmpty()
                                        && XposedServiceBridge.isServiceAvailable()) {
                                    if (ConfigManager.loadFromRemotePreferences()) {
                                        BuildHooks.refreshStaticFields(lpparam.classLoader);
                                    }
                                }
                                Log.i(TAG, "After Application.attach pkg=" + lpparam.packageName
                                        + " imei=" + ConfigManager.getIdentifierValue("imei")
                                        + " gaid=" + ConfigManager.getIdentifierValue("gaid"));
                            } catch (Throwable t) {
                                Log.w(TAG, "Application.attach hook failed: "
                                        + t.getMessage());
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook Application.attach: " + t.getMessage());
        }

        // BuildHooks first so direct Build.* reads pick up spoofed values.
        try {
            BuildHooks.hook(lpparam);
            logInfo(verbose, TAG + ": BuildHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": BuildHooks failed: " + e.getMessage());
        }

        // Skip SystemProperties / Locale / Display / Native in our own process so
        // MainActivity (the config editor) reads the real host values instead of
        // looping back its own spoof.
        if (!isOwnPackage) {
            try {
                SystemPropertiesHooks.hook(lpparam);
                logInfo(verbose, TAG + ": SystemPropertiesHooks loaded");
            } catch (Exception e) {
                XposedBridge.log(TAG + ": SystemPropertiesHooks failed: " + e.getMessage());
            }
        } else {
            logInfo(verbose, TAG + ": SystemPropertiesHooks skipped for module process");
        }

        try {
            HardwareHooks.hook(lpparam);
            logInfo(verbose, TAG + ": HardwareHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": HardwareHooks failed: " + e.getMessage());
        }

        try {
            TelephonyHooks.hook(lpparam);
            logInfo(verbose, TAG + ": TelephonyHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": TelephonyHooks failed: " + e.getMessage());
        }

        try {
            SettingsHooks.hook(lpparam);
            logInfo(verbose, TAG + ": SettingsHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": SettingsHooks failed: " + e.getMessage());
        }

        try {
            AdvertisingIdHooks.hook(lpparam);
            logInfo(verbose, TAG + ": AdvertisingIdHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": AdvertisingIdHooks failed: " + e.getMessage());
        }

        if (realDeviceSdk >= 30) {
            try {
                AppSetIdHooks.hook(lpparam, realDeviceSdk);
                logInfo(verbose, TAG + ": AppSetIdHooks loaded");
            } catch (Exception e) {
                XposedBridge.log(TAG + ": AppSetIdHooks failed: " + e.getMessage());
            }
        }

        try {
            MediaDrmHooks.hook(lpparam);
            logInfo(verbose, TAG + ": MediaDrmHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": MediaDrmHooks failed: " + e.getMessage());
        }

        try {
            WebViewHooks.hook(lpparam);
            logInfo(verbose, TAG + ": WebViewHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": WebViewHooks failed: " + e.getMessage());
        }

        try {
            PackageManagerHooks.hook(lpparam);
            logInfo(verbose, TAG + ": PackageManagerHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": PackageManagerHooks failed: " + e.getMessage());
        }

        try {
            NetworkHooks.hook(lpparam);
            logInfo(verbose, TAG + ": NetworkHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": NetworkHooks failed: " + e.getMessage());
        }

        if (!isOwnPackage) {
            try {
                DisplayHooks.hook(lpparam);
                logInfo(verbose, TAG + ": DisplayHooks loaded");
            } catch (Exception e) {
                XposedBridge.log(TAG + ": DisplayHooks failed: " + e.getMessage());
            }
        } else {
            logInfo(verbose, TAG + ": DisplayHooks skipped for module process");
        }

        try {
            SensorHooks.hook(lpparam);
            logInfo(verbose, TAG + ": SensorHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": SensorHooks failed: " + e.getMessage());
        }

        try {
            CameraHooks.hook(lpparam);
            logInfo(verbose, TAG + ": CameraHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": CameraHooks failed: " + e.getMessage());
        }

        try {
            StorageHooks.hook(lpparam);
            logInfo(verbose, TAG + ": StorageHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": StorageHooks failed: " + e.getMessage());
        }

        if (!isOwnPackage) {
            try {
                LocaleHooks.hook(lpparam);
                logInfo(verbose, TAG + ": LocaleHooks loaded");
            } catch (Exception e) {
                XposedBridge.log(TAG + ": LocaleHooks failed: " + e.getMessage());
            }
        } else {
            logInfo(verbose, TAG + ": LocaleHooks skipped for module process");
        }

        if (ConfigManager.isHideAccountsEnabled()) {
            try {
                AccountHooks.hook(lpparam);
                logInfo(verbose, TAG + ": AccountHooks loaded");
            } catch (Exception e) {
                XposedBridge.log(TAG + ": AccountHooks failed: " + e.getMessage());
            }
        } else {
            logInfo(verbose, TAG + ": AccountHooks skipped (hooks.hide_accounts=0)");
        }

        try {
            PackageInfoHooks.hook(lpparam);
            logInfo(verbose, TAG + ": PackageInfoHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": PackageInfoHooks failed: " + e.getMessage());
        }

        try {
            BatteryHooks.hook(lpparam);
            logInfo(verbose, TAG + ": BatteryHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": BatteryHooks failed: " + e.getMessage());
        }

        if (realDeviceSdk >= 28) {
            try {
                EuiccHooks.hook(lpparam, realDeviceSdk);
                logInfo(verbose, TAG + ": EuiccHooks loaded");
            } catch (Exception e) {
                XposedBridge.log(TAG + ": EuiccHooks failed: " + e.getMessage());
            }
        }

        try {
            InputDeviceHooks.hook(lpparam);
            logInfo(verbose, TAG + ": InputDeviceHooks loaded");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": InputDeviceHooks failed: " + e.getMessage());
        }

        if (!isOwnPackage) {
            try {
                boolean ok = NativeHooks.tryInstall(ConfigManager.getAllSpoofedProperties());
                logInfo(verbose, TAG + (ok
                        ? ": NativeHooks loaded"
                        : ": NativeHooks unavailable (Java-only spoofing active)"));
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": NativeHooks failed: " + t.getMessage());
            }
        } else {
            logInfo(verbose, TAG + ": NativeHooks skipped for module process");
        }

        logInfo(verbose, TAG + ": All hooks initialized for " + lpparam.packageName);
    }

    private static void logInfo(boolean verbose, String message) {
        if (verbose) {
            XposedBridge.log(message);
        }
    }
}
