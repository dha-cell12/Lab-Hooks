package com.devicespooflab.hooks.hooks;

import android.os.BatteryManager;

import com.devicespooflab.hooks.utils.ConfigManager;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

// Live charge level is passthrough; only the design counters are overridden.
public class BatteryHooks {

    private static final String TAG = "DeviceSpoofLab-Battery";

    public static void hook(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(BatteryManager.class, "getIntProperty",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int id = (int) param.args[0];
                            if (id == BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) {
                                long capUah = ConfigManager.getBatteryChargeCounterUah();
                                param.setResult((int) Math.min(Integer.MAX_VALUE, capUah));
                            }
                        }
                    });
        } catch (Throwable t) { logFail("getIntProperty", t); }

        try {
            XposedHelpers.findAndHookMethod(BatteryManager.class, "getLongProperty",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int id = (int) param.args[0];
                            if (id == BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) {
                                param.setResult(ConfigManager.getBatteryChargeCounterUah());
                            } else if (id == BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) {
                                param.setResult(ConfigManager.getBatteryEnergyCounterNwh());
                            }
                        }
                    });
        } catch (Throwable t) { logFail("getLongProperty", t); }
    }

    private static void logFail(String what, Throwable t) {
        XposedBridge.log(TAG + ": failed to hook BatteryManager." + what + ": " + t);
    }
}
