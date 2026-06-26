package com.devicespooflab.hooks;

import android.util.Log;
import com.devicespooflab.hooks.hooks.*;
import com.devicespooflab.hooks.utils.ConfigManager;
import java.util.Map;

public class ZygiskEntry {
    private static final String TAG = "DeviceSpoofLab-ZygiskEntry";

    public static void init(ClassLoader classLoader, String processName) {
        Log.i(TAG, "Initializing Java hooks from Zygisk for process: " + processName);
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }

        try {
            AccountHooks.hook(classLoader, processName);
            AdvertisingIdHooks.hook(classLoader, processName);
            AppSetIdHooks.hook(classLoader, processName);
            BatteryHooks.hook(classLoader, processName);
            BuildHooks.hook(classLoader, processName);
            CameraHooks.hook(classLoader, processName);
            DisplayHooks.hook(classLoader, processName);
            EuiccHooks.hook(classLoader, processName);
            HardwareHooks.hook(classLoader, processName);
            InputDeviceHooks.hook(classLoader, processName);
            LocaleHooks.hook(classLoader, processName);
            MediaDrmHooks.hook(classLoader, processName);
            NetworkHooks.hook(classLoader, processName);
            PackageInfoHooks.hook(classLoader, processName);
            PackageManagerHooks.hook(classLoader, processName);
            SensorHooks.hook(classLoader, processName);
            SettingsHooks.hook(classLoader, processName);
            StorageHooks.hook(classLoader, processName);
            SystemPropertiesHooks.hook(classLoader, processName);
            TelephonyHooks.hook(classLoader, processName);
            WebViewHooks.hook(classLoader, processName);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init Zygisk Java hooks", t);
        }
    }

    public static void setConfig(Map<String, String> config) {
        ConfigManager.setProperties(config);
        Log.i(TAG, "Config updated from Zygisk: " + config.size() + " entries");
    }

    public static Class<?> findClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, true, loader);
        } catch (Exception e) {
            return null;
        }
    }
}
