package com.devicespooflab.hooks.hooks;

import android.accounts.Account;

import com.devicespooflab.hooks.utils.ConfigManager;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

// AccountManager.getAccounts is hooked; getAccountsByType is intentionally not.
public class AccountHooks {

    private static final String TAG = "DeviceSpoofLab-Account";

    public static void hook(LoadPackageParam lpparam) {
        if (!ConfigManager.isHideAccountsEnabled()) {
            return;
        }

        Class<?> am = XposedHelpers.findClassIfExists(
                "android.accounts.AccountManager", lpparam.classLoader);
        if (am == null) return;

        try {
            XposedHelpers.findAndHookMethod(am, "getAccounts",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new Account[0]);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook getAccounts: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(am, "getAccountsAsUser",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(new Account[0]);
                        }
                    });
        } catch (Throwable t) { /* hidden API; may be missing */ }
    }
}
