package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import com.devicespooflab.hooks.utils.ConfigManager;






// Install times reported as 60-120 days ago, derived from android_id so the
// per-install value stays stable across reads.
public class PackageInfoHooks {

    private static final String TAG = "DeviceSpoofLab-PackageInfo";
    private static final long DAY_MS = 86_400_000L;

    public static void hook(ClassLoader classLoader, String processName) {
        hookPackageInfoFields(classLoader, processName);
        hookGetInstallerPackageName(classLoader, processName);
        hookGetInstallSourceInfo(classLoader, processName);
    }

    private static void hookPackageInfoFields(ClassLoader classLoader, String processName) {
        Class<?> appPm = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.app.ApplicationPackageManager", classLoader);
        if (appPm == null) return;

        ZygiskMethodHook patcher = new ZygiskMethodHook() {
            @Override
            public void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (result instanceof PackageInfo) {
                    patch((PackageInfo) result);
                }
            }
        };

        // getPackageInfo(String, int) and getPackageInfo(String, PackageInfoFlags)
        try {
            LSPlantJavaWrapper.findAndHookMethod(appPm, "getPackageInfo",
                    String.class, int.class, patcher);
        } catch (Throwable t) { logFail("getPackageInfo(String,int)", t); }

        try {
            Class<?> flags = com.devicespooflab.hooks.ZygiskEntry.findClass(
                    "android.content.pm.PackageManager$PackageInfoFlags", classLoader);
            if (flags != null) {
                LSPlantJavaWrapper.findAndHookMethod(appPm, "getPackageInfo",
                        String.class, flags, patcher);
            }
        } catch (Throwable t) { /* Android 13+ overload */ }
    }

    private static void hookGetInstallerPackageName(ClassLoader classLoader, String processName) {
        Class<?> appPm = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.app.ApplicationPackageManager", classLoader);
        if (appPm == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(appPm, "getInstallerPackageName",
                    String.class,
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[0];
                            if (shouldSpoofInstaller(processName, packageName)) {
                                param.setResult(ConfigManager.getInstallerPackage());
                            }
                        }
                    });
        } catch (Throwable t) { logFail("getInstallerPackageName", t); }
    }

    private static boolean shouldSpoofInstaller(String processName,
                                                String packageName) {
        if (processName == null) {
            return false;
        }
        // Spoof if we are checking the installer of the current process's package
        if (!processName.equals(packageName)) {
            return false;
        }
        if (ConfigManager.isOwnPackageProcess(processName)) {
            return false;
        }

        // Only spoof for non-system apps to avoid breaking system components
        ApplicationInfo appInfo = ConfigManager.getApplicationInfo(processName);
        if (appInfo == null) {
            return true;
        }
        int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return (appInfo.flags & systemFlags) == 0;
    }

    private static void hookGetInstallSourceInfo(ClassLoader classLoader, String processName) {
        Class<?> appPm = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.app.ApplicationPackageManager", classLoader);
        if (appPm == null) return;

        Class<?> sourceInfo = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.content.pm.InstallSourceInfo", classLoader);
        if (sourceInfo == null) return;

        // InstallSourceInfo getters — hook each accessor to return Play Store.
        ZygiskMethodHook playStoreHook = new ZygiskMethodHook() {
            @Override
            public void afterHookedMethod(MethodHookParam param) {
                param.setResult(ConfigManager.getInstallerPackage());
            }
        };

        for (String getter : new String[]{
                "getInstallingPackageName",
                "getInitiatingPackageName",
                "getOriginatingPackageName"
        }) {
            try {
                LSPlantJavaWrapper.findAndHookMethod(sourceInfo, getter, playStoreHook);
            } catch (Throwable t) { /* getter may be absent on older Android */ }
        }
    }

    private static void patch(PackageInfo pi) {
        if (pi == null) return;
        long install = stableInstallTime();
        pi.firstInstallTime = install;
        // lastUpdateTime: random within 0–14 days after install, stable per app.
        pi.lastUpdateTime = install + (Math.abs(stableHash(pi.packageName)) % (14L * DAY_MS));
    }

    private static long stableInstallTime() {
        // 60–120 days before now, stable per android_id.
        long seed = Math.abs(ConfigManager.getFingerprintSeed());
        long offsetDays = 60 + (seed % 61);
        return System.currentTimeMillis() - offsetDays * DAY_MS;
    }

    private static long stableHash(String s) {
        if (s == null) return 0L;
        long h = 1469598103934665603L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 1099511628211L;
        }
        return h;
    }

    private static void logFail(String what, Throwable t) {
        android.util.Log.i(TAG, "failed to hook " + what + ": " + t);
    }



}
