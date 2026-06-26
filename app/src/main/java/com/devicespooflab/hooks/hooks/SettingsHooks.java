package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.content.ContentResolver;

import com.devicespooflab.hooks.utils.ConfigManager;





public class SettingsHooks {

    private static final int SPOOF_ANDROID_ID = 1;
    private static final int SPOOF_GSF_ID = 1 << 1;
    private static final int SPOOF_BLUETOOTH_ADDRESS = 1 << 2;
    private static final int SPOOF_DEVICE_NAMES = 1 << 3;

    private static final String ANDROID_ID = "android_id";
    private static final String GSF_ID = "gsf_id";
    private static final String BLUETOOTH_ADDRESS = "bluetooth_address";
    private static final String BLUETOOTH_NAME = "bluetooth_name";
    private static final String DEVICE_NAME = "device_name";

    public static void hook(ClassLoader classLoader) {
        hookClass(lpparam, "android.provider.Settings$Secure",
                SPOOF_ANDROID_ID | SPOOF_GSF_ID | SPOOF_BLUETOOTH_ADDRESS | SPOOF_DEVICE_NAMES);
        hookClass(lpparam, "android.provider.Settings$System",
                SPOOF_BLUETOOTH_ADDRESS | SPOOF_DEVICE_NAMES);
        hookClass(lpparam, "android.provider.Settings$Global",
                SPOOF_BLUETOOTH_ADDRESS | SPOOF_DEVICE_NAMES);

}

    private static void hookClass(XC_LoadPackage.LoadPackageParam lpparam,
                                  String className,
                                  int spoofFlags) {
        Class<?> clazz = findClass(className, classLoader);
        if (clazz == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(clazz, "getString",
                    ContentResolver.class, String.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String name = (String) param.args[1];
                            applySpoof(param, name, spoofFlags);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(clazz, "getString",
                    ContentResolver.class, String.class, String.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String name = (String) param.args[1];
                            applySpoof(param, name, spoofFlags);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(clazz, "getStringForUser",
                    ContentResolver.class, String.class, int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String name = (String) param.args[1];
                            applySpoof(param, name, spoofFlags);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }
    }

    private static void applySpoof(XC_MethodHook.MethodHookParam param, String name, int spoofFlags) {
        if (name == null) return;

        if ((spoofFlags & SPOOF_ANDROID_ID) != 0 && ANDROID_ID.equals(name)) {
            String v = ConfigManager.getAndroidId();
            if (v != null) param.setResult(v);
            return;
        }

        if ((spoofFlags & SPOOF_GSF_ID) != 0 && GSF_ID.equals(name)) {
            String v = ConfigManager.getGSFId();
            if (v != null) param.setResult(v);
            return;
        }

        if ((spoofFlags & SPOOF_BLUETOOTH_ADDRESS) != 0 && BLUETOOTH_ADDRESS.equals(name)) {
            String mac = ConfigManager.getBluetoothMacAddress();
            if (mac != null) param.setResult(mac.toUpperCase());
            return;
        }

        if ((spoofFlags & SPOOF_DEVICE_NAMES) != 0
                && (BLUETOOTH_NAME.equals(name) || DEVICE_NAME.equals(name))) {
            String model = ConfigManager.getBuildModel();
            if (model != null) param.setResult(model);
        }
    }

    private static Class<?> findClass(String name, ClassLoader loader) { try { return Class.forName(name, true, loader); } catch (Exception e) { return null; } }
}
