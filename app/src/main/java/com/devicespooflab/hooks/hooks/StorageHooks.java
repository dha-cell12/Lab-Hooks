package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.os.StatFs;

import com.devicespooflab.hooks.utils.ConfigManager;






public class StorageHooks {

    private static final String TAG = "DeviceSpoofLab-Storage";
    private static final long BLOCK_SIZE = 4096L;

    public static void hook(ClassLoader classLoader, String processName) {
        try {
            LSPlantJavaWrapper.findAndHookMethod(StatFs.class, "getBlockSizeLong",
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            param.setResult(BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getBlockSizeLong", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(StatFs.class, "getBlockCountLong",
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageTotalBytes() / BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getBlockCountLong", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(StatFs.class, "getAvailableBlocksLong",
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes() / BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getAvailableBlocksLong", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(StatFs.class, "getFreeBlocksLong",
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes() / BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getFreeBlocksLong", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(StatFs.class, "getTotalBytes",
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageTotalBytes());
                        }
                    });
        } catch (Throwable t) { logFail("getTotalBytes", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(StatFs.class, "getAvailableBytes",
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes());
                        }
                    });
        } catch (Throwable t) { logFail("getAvailableBytes", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(StatFs.class, "getFreeBytes",
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes());
                        }
                    });
        } catch (Throwable t) { logFail("getFreeBytes", t); }
    }

    private static void logFail(String what, Throwable t) {
        android.util.Log.i(TAG, "failed to hook StatFs." + what + ": " + t);
    }



}
