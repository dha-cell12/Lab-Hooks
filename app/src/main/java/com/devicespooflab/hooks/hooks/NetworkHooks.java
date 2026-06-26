package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import com.devicespooflab.hooks.utils.ConfigManager;

import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;






public class NetworkHooks {

    private static final String TAG = "DeviceSpoofLab-Network";
    private static final byte[] EMPTY_MAC = new byte[0];

    public static void hook(ClassLoader classLoader) {
        hookWifiInfo(lpparam);
        hookWifiManager(lpparam);
        hookBluetoothAdapter(lpparam);
        hookNetworkInterface();
    }

    private static void hookWifiInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> wifiInfo = findClass(
                "android.net.wifi.WifiInfo", classLoader);
        if (wifiInfo == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(wifiInfo, "getMacAddress",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getWifiMacAddress();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (Throwable t) { logFail("WifiInfo.getMacAddress", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(wifiInfo, "getBSSID",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getWifiBssid();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (Throwable t) { logFail("WifiInfo.getBSSID", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(wifiInfo, "getSSID",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult("\"" + ConfigManager.getWifiSsid() + "\"");
                        }
                    });
        } catch (Throwable t) { logFail("WifiInfo.getSSID", t); }
    }

    private static void hookWifiManager(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> wm = findClass(
                "android.net.wifi.WifiManager", classLoader);
        if (wm == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(wm, "getScanResults",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // Scan-result MAC addresses are equally fingerprintable;
                            // empty list is the safest spoof.
                            param.setResult(Collections.emptyList());
                        }
                    });
        } catch (Throwable t) { logFail("WifiManager.getScanResults", t); }

        // Some apps reach into WifiManager.getCurrentNetwork().getSSID() — those go
        // through WifiInfo, already covered.
    }

    private static void hookBluetoothAdapter(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> ba = findClass(
                "android.bluetooth.BluetoothAdapter", classLoader);
        if (ba == null) return;

        try {
            LSPlantJavaWrapper.findAndHookMethod(ba, "getAddress",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String mac = ConfigManager.getBluetoothMacAddress();
                            if (mac != null) param.setResult(mac.toUpperCase());
                        }
                    });
        } catch (Throwable t) { logFail("BluetoothAdapter.getAddress", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(ba, "getName",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(ConfigManager.getBluetoothName());
                        }
                    });
        } catch (Throwable t) { logFail("BluetoothAdapter.getName", t); }

        // Settings.Secure.bluetooth_address path — settings hook handles strings,
        // but BluetoothAdapter.getAddress hides the well-known reflection too.
    }

    private static void hookNetworkInterface() {
        try {
            LSPlantJavaWrapper.findAndHookMethod(NetworkInterface.class, "getHardwareAddress",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            NetworkInterface ni = (NetworkInterface) param.thisObject;
                            String name = (ni == null) ? null : ni.getName();
                            if (name == null) return;
                            // Loopback and dummy interfaces have no MAC; preserve null.
                            byte[] original = (byte[]) param.getResult();
                            if (original == null) return;

                            String mac;
                            if (name.startsWith("wlan")) {
                                mac = ConfigManager.getWifiMacAddress();
                            } else if (name.startsWith("bt") || name.startsWith("bnep")) {
                                mac = ConfigManager.getBluetoothMacAddress();
                            } else {
                                // Other interfaces: zero them out rather than leak.
                                param.setResult(new byte[]{0, 0, 0, 0, 0, 0});
                                return;
                            }
                            if (mac == null) return;
                            param.setResult(macStringToBytes(mac));
                        }
                    });
        } catch (Throwable t) { logFail("NetworkInterface.getHardwareAddress", t); }

        try {
            LSPlantJavaWrapper.findAndHookMethod(NetworkInterface.class, "getNetworkInterfaces",
                    new ZygiskMethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) {
                            Enumeration<NetworkInterface> orig =
                                    (Enumeration<NetworkInterface>) param.getResult();
                            if (orig == null) return;

                            // Filter out interfaces named "rmnet*" / "ccmni*" / "p2p*"
                            // which leak modem/p2p details on emulators.
                            List<NetworkInterface> kept = new ArrayList<>();
                            while (orig.hasMoreElements()) {
                                NetworkInterface ni = orig.nextElement();
                                String n = (ni == null) ? "" : ni.getName();
                                if (n == null) continue;
                                if (n.startsWith("rmnet") || n.startsWith("ccmni")
                                        || n.startsWith("p2p") || n.startsWith("dummy")) {
                                    continue;
                                }
                                kept.add(ni);
                            }
                            param.setResult(Collections.enumeration(kept));
                        }
                    });
        } catch (Throwable t) { logFail("NetworkInterface.getNetworkInterfaces", t); }
    }

    private static byte[] macStringToBytes(String mac) {
        if (mac == null) return EMPTY_MAC;
        String[] parts = mac.split(":");
        if (parts.length != 6) return EMPTY_MAC;
        byte[] out = new byte[6];
        try {
            for (int i = 0; i < 6; i++) {
                out[i] = (byte) Integer.parseInt(parts[i], 16);
            }
        } catch (NumberFormatException e) {
            return EMPTY_MAC;
        }
        return out;
    }

    private static void logFail(String what, Throwable t) {
        android.util.Log.i(TAG + ": failed to hook " + what + ": " + t);
    }


    private static Class<?> findClass(String name, ClassLoader loader) { try { return Class.forName(name, true, loader); } catch (Exception e) { return null; } }
}
