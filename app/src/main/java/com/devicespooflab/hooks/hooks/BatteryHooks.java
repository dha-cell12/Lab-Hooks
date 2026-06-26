package com.devicespooflab.hooks.hooks;

import android.os.BatteryManager;
import com.devicespooflab.hooks.utils.ConfigManager;
import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

public class BatteryHooks {
    public static void hook(ClassLoader classLoader, String processName) {
        try {
            LSPlantJavaWrapper.findAndHookMethod(BatteryManager.class, "getIntProperty",
                    int.class,
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            int id = (int) param.args[0];
                            if (id == BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) {
                                long capUah = ConfigManager.getBatteryChargeCounterUah();
                                param.setResult((int) Math.min(Integer.MAX_VALUE, capUah));
                            }
                        }
                    });
        } catch (Throwable t) {}

        try {
            LSPlantJavaWrapper.findAndHookMethod(BatteryManager.class, "getLongProperty",
                    int.class,
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
                            int id = (int) param.args[0];
                            if (id == BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) {
                                param.setResult(ConfigManager.getBatteryChargeCounterUah());
                            } else if (id == BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) {
                                param.setResult(ConfigManager.getBatteryEnergyCounterNwh());
                            }
                        }
                    });
        } catch (Throwable t) {}
    }



}
