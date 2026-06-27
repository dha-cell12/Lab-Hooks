package com.devicespooflab.hooks.hooks;

import android.os.StatFs;

import com.devicespooflab.hooks.utils.ConfigManager;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class StorageHooks {

    private static final String TAG = "DeviceSpoofLab-Storage";
    private static final long BLOCK_SIZE = 4096L;

    public static void hook(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(StatFs.class, "getBlockSizeLong",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getBlockSizeLong", t); }

        try {
            XposedHelpers.findAndHookMethod(StatFs.class, "getBlockCountLong",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageTotalBytes() / BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getBlockCountLong", t); }

        try {
            XposedHelpers.findAndHookMethod(StatFs.class, "getAvailableBlocksLong",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes() / BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getAvailableBlocksLong", t); }

        try {
            XposedHelpers.findAndHookMethod(StatFs.class, "getFreeBlocksLong",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes() / BLOCK_SIZE);
                        }
                    });
        } catch (Throwable t) { logFail("getFreeBlocksLong", t); }

        try {
            XposedHelpers.findAndHookMethod(StatFs.class, "getTotalBytes",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageTotalBytes());
                        }
                    });
        } catch (Throwable t) { logFail("getTotalBytes", t); }

        try {
            XposedHelpers.findAndHookMethod(StatFs.class, "getAvailableBytes",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes());
                        }
                    });
        } catch (Throwable t) { logFail("getAvailableBytes", t); }

        try {
            XposedHelpers.findAndHookMethod(StatFs.class, "getFreeBytes",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getStorageAvailableBytes());
                        }
                    });
        } catch (Throwable t) { logFail("getFreeBytes", t); }
    }

    private static void logFail(String what, Throwable t) {
        XposedBridge.log(TAG + ": failed to hook StatFs." + what + ": " + t);
    }
}
