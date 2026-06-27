package com.devicespooflab.hooks.hooks;

import android.hardware.Sensor;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.List;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class SensorHooks {

    private static final String TAG = "DeviceSpoofLab-Sensor";
    private static final String[] DENY = {"goldfish", "ranchu", "emulator", "qemu", "vbox"};

    public static void hook(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(SensorManager.class, "getSensorList",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) {
                            List<Sensor> orig = (List<Sensor>) param.getResult();
                            param.setResult(filter(orig));
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook getSensorList: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(SensorManager.class, "getDefaultSensor",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Sensor s = (Sensor) param.getResult();
                            if (s != null && isEmulatorSensor(s)) {
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook getDefaultSensor: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(SensorManager.class, "getDefaultSensor",
                    int.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Sensor s = (Sensor) param.getResult();
                            if (s != null && isEmulatorSensor(s)) {
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable t) { /* version-specific overload */ }
    }

    private static List<Sensor> filter(List<Sensor> orig) {
        if (orig == null) return new ArrayList<>();
        List<Sensor> kept = new ArrayList<>(orig.size());
        for (Sensor s : orig) {
            if (s == null) continue;
            if (!isEmulatorSensor(s)) kept.add(s);
        }
        return kept;
    }

    private static boolean isEmulatorSensor(Sensor s) {
        String name = nullSafe(s.getName()).toLowerCase();
        String vendor = nullSafe(s.getVendor()).toLowerCase();
        for (String token : DENY) {
            if (name.contains(token) || vendor.contains(token)) return true;
        }
        return false;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
