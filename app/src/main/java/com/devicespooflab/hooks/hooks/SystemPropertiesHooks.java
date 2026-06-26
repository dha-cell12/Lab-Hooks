package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import com.devicespooflab.hooks.utils.ConfigManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;






public class SystemPropertiesHooks {

    private static final String TAG = "DeviceSpoofLab-SystemProps";
    private static final String SYSTEM_PROPERTIES_CLASS = "android.os.SystemProperties";
    private static final Set<Class<?>> HOOKED_CLASSES =
            Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    public static void hook(ClassLoader classLoader, String processName) {
        try {
            hookSystemProperties(classLoader);

            try {
                ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
                if (systemClassLoader != null && systemClassLoader != classLoader) {
                    hookSystemProperties(systemClassLoader);

}
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            android.util.Log.i(TAG, "Failed to hook SystemProperties: " + e.getMessage());
        }
    }

    private static void hookSystemProperties(ClassLoader classLoader) {
        Class<?> sysPropClass = com.devicespooflab.hooks.ZygiskEntry.findClass(SYSTEM_PROPERTIES_CLASS, classLoader);

        if (sysPropClass == null) {
            return;
        }
        if (!HOOKED_CLASSES.add(sysPropClass)) {
            return;
        }

        // Hook get(String key)
        try {
            LSPlantJavaWrapper.findAndHookMethod(sysPropClass, "get",
                String.class,
                new ZygiskMethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String originalValue = (String) param.getResult();
                        String spoofedValue = ConfigManager.getSystemProperty(key, null);

                        if (spoofedValue != null) {
                            param.setResult(spoofedValue);
                        }
                    }
                });
        } catch (Exception e) {
            android.util.Log.i(TAG, "Failed to hook get(String): " + e.getMessage());
        }

        // Hook get(String key, String def)
        try {
            LSPlantJavaWrapper.findAndHookMethod(sysPropClass, "get",
                String.class, String.class,
                new ZygiskMethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String defaultValue = (String) param.args[1];
                        String spoofedValue = ConfigManager.getSystemProperty(key, null);

                        if (spoofedValue != null) {
                            param.setResult(spoofedValue);
                        }
                    }
                });
        } catch (Exception e) {
            android.util.Log.i(TAG, "Failed to hook get(String, String): " + e.getMessage());
        }

        // Hook getInt(String key, int def)
        try {
            LSPlantJavaWrapper.findAndHookMethod(sysPropClass, "getInt",
                String.class, int.class,
                new ZygiskMethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String spoofedValue = ConfigManager.getSystemProperty(key, null);

                        if (spoofedValue != null) {
                            try {
                                int intValue = Integer.parseInt(spoofedValue);
                                param.setResult(intValue);
                            } catch (NumberFormatException e) {
                                // Invalid int value, keep original
                            }
                        }
                    }
                });
        } catch (Exception e) {
            android.util.Log.i(TAG, "Failed to hook getInt(String, int): " + e.getMessage());
        }

        // Hook getBoolean(String key, boolean def)
        try {
            LSPlantJavaWrapper.findAndHookMethod(sysPropClass, "getBoolean",
                String.class, boolean.class,
                new ZygiskMethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String spoofedValue = ConfigManager.getSystemProperty(key, null);

                        if (spoofedValue != null) {
                            // Handle both "true"/"false" and "1"/"0"
                            boolean boolValue = spoofedValue.equals("1") ||
                                              spoofedValue.equalsIgnoreCase("true");
                            param.setResult(boolValue);
                        }
                    }
                });
        } catch (Exception e) {
            android.util.Log.i(TAG, "Failed to hook getBoolean(String, boolean): " + e.getMessage());
        }

        // Hook getLong(String key, long def)
        try {
            LSPlantJavaWrapper.findAndHookMethod(sysPropClass, "getLong",
                String.class, long.class,
                new ZygiskMethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        String spoofedValue = ConfigManager.getSystemProperty(key, null);

                        if (spoofedValue != null) {
                            try {
                                long longValue = Long.parseLong(spoofedValue);
                                param.setResult(longValue);
                            } catch (NumberFormatException e) {
                                // Invalid long value, keep original
                            }
                        }
                    }
                });
        } catch (Exception e) {
            android.util.Log.i(TAG, "Failed to hook getLong(String, long): " + e.getMessage());
        }
    }


}
