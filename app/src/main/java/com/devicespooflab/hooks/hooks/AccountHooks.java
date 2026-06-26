package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import android.accounts.Account;

import com.devicespooflab.hooks.utils.ConfigManager;






// AccountManager.getAccounts is hooked; getAccountsByType is intentionally not.
public class AccountHooks {

    private static final String TAG = "DeviceSpoofLab-Account";

    public static void hook(ClassLoader classLoader, String processName) {
        if (!ConfigManager.isHideAccountsEnabled()) {
            return;
        }

        Class<?> am = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.accounts.AccountManager", classLoader);
        if (am == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(am, "getAccounts",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new Account[0]);
                        }
                    });
        } catch (Throwable t) {
            android.util.Log.i(TAG + ": failed to hook getAccounts: " + t);
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(am, "getAccountsAsUser",
                    int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new Account[0]);
                        }
                    });
        } catch (Throwable t) { /* hidden API; may be missing */ }
    }



}
