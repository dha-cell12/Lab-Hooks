package com.devicespooflab.hooks;

import android.util.Log;
import com.devicespooflab.hooks.hooks.BatteryHooks;
import com.devicespooflab.hooks.utils.ConfigManager;
import java.util.Map;

public class ZygiskEntry {
    private static final String TAG = "DeviceSpoofLab-ZygiskEntry";

    public static void init(ClassLoader classLoader) {
        Log.i(TAG, "Initializing Java hooks from Zygisk");
        if (classLoader == null) {
            classLoader = ZygiskEntry.class.getClassLoader();
        }
        try {
            com.devicespooflab.hooks.hooks.AccountHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.AdvertisingIdHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.AppSetIdHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.BatteryHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.BuildHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.CameraHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.DisplayHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.EuiccHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.HardwareHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.InputDeviceHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.LocaleHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.MediaDrmHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.NetworkHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.PackageInfoHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.PackageManagerHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.SensorHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.SettingsHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.StorageHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.SystemPropertiesHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.TelephonyHooks.hook(classLoader);
            com.devicespooflab.hooks.hooks.WebViewHooks.hook(classLoader);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init Zygisk Java hooks", t);
        }
    }

    public static void setConfig(Map<String, String> config) {
        ConfigManager.setProperties(config);
        Log.i(TAG, "Config updated from Zygisk: " + config.size() + " entries");
    }
}
