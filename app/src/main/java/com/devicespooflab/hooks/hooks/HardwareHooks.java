package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.app.ActivityManager;
import android.os.Debug;

import com.devicespooflab.hooks.utils.ConfigManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;






public class HardwareHooks {

    private static final String TAG = "DeviceSpoofLab-Hardware";
    private static final AtomicBoolean RUNTIME_CORES_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean DEBUG_MEMORY_HOOKED = new AtomicBoolean(false);
    private static final Set<Class<?>> HOOKED_ACTIVITY_MANAGER_CLASSES =
            Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    public static void hook(ClassLoader classLoader, String processName) {
        try {
            hookRuntimeCores();
            hookActivityManagerMemory(lpparam);
            hookDebugMemory();
            if (ConfigManager.isVerboseLoggingEnabled()) {
                android.util.Log.i(TAG + ": Successfully hooked hardware specs");
            }
        } catch (Exception e) {
            android.util.Log.i(TAG + ": Failed to hook hardware: " + e.getMessage());
        }
    }

    private static void hookRuntimeCores() {
        if (!RUNTIME_CORES_HOOKED.compareAndSet(false, true)) {
            return;
        }
        try {
            LSPlantJavaWrapper.findAndHookMethod(Runtime.class, "availableProcessors",
                new ZygiskMethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(ConfigManager.getCpuCoreCount());
                    }
                });
        } catch (Exception e) {
            RUNTIME_CORES_HOOKED.set(false);
            android.util.Log.i(TAG + ": Failed to hook Runtime.availableProcessors(): " + e.getMessage());
        }
    }

    private static void hookActivityManagerMemory(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityManagerClass = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.app.ActivityManager", classLoader);

            if (activityManagerClass == null) {
                return;
            }
            if (!HOOKED_ACTIVITY_MANAGER_CLASSES.add(activityManagerClass)) {
                return;
            }

            LSPlantJavaWrapper.findAndHookMethod(activityManagerClass, "getMemoryInfo",
                ActivityManager.MemoryInfo.class,
                new ZygiskMethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ActivityManager.MemoryInfo memInfo = (ActivityManager.MemoryInfo) param.args[0];
                        if (memInfo != null) {
                            long originalTotal = memInfo.totalMem;
                            long configuredTotal = Math.max(0L, ConfigManager.getMemoryTotalBytes());
                            long configuredAvailable = Math.max(0L, ConfigManager.getMemoryAvailableKb() * 1024L);
                            memInfo.totalMem = configuredTotal;
                            if (originalTotal > 0) {
                                long originalAvailable = Math.max(0L,
                                        Math.min(originalTotal, memInfo.availMem));
                                double availableRatio = (double) originalAvailable / originalTotal;
                                memInfo.availMem = Math.min(configuredTotal,
                                        (long) (configuredTotal * availableRatio));
                            } else {
                                memInfo.availMem = Math.min(configuredTotal, configuredAvailable);
                            }
                        }
                    }
                });

            LSPlantJavaWrapper.findAndHookMethod(activityManagerClass, "getMemoryClass",
                new ZygiskMethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(ConfigManager.getMemoryClassMb());
                    }
                });

            LSPlantJavaWrapper.findAndHookMethod(activityManagerClass, "getLargeMemoryClass",
                new ZygiskMethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(ConfigManager.getLargeMemoryClassMb());
                    }
                });

        } catch (Exception e) {
            android.util.Log.i(TAG + ": Failed to hook ActivityManager memory: " + e.getMessage());
        }
    }

    private static void hookDebugMemory() {
        if (!DEBUG_MEMORY_HOOKED.compareAndSet(false, true)) {
            return;
        }
        try {
            LSPlantJavaWrapper.findAndHookMethod(Debug.class, "getNativeHeapSize",
                new ZygiskMethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        long originalSize = (Long) param.getResult();
                        param.setResult(originalSize * Math.max(1, ConfigManager.getNativeHeapScale()));
                    }
                });
        } catch (Exception e) {
            DEBUG_MEMORY_HOOKED.set(false);
        }
    }



}
