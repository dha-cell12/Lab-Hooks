package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import com.devicespooflab.hooks.utils.ConfigManager;





public class TelephonyHooks {

    public static void hook(ClassLoader classLoader, String processName) {
        Class<?> telephonyManager = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.telephony.TelephonyManager",
                classLoader
        );

        if (telephonyManager == null) {
            return;
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getDeviceId",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getIMEI();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getDeviceId", int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getIMEI();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getImei",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getIMEI();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getImei", int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getIMEI();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getMeid",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getMEID();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getMeid", int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getMEID();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getSubscriberId",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getIMSI();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getSubscriberId", int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getIMSI();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getSimSerialNumber",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getICCID();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getSimSerialNumber", int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getICCID();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getLine1Number",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getPhoneNumber();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getLine1Number", int.class,
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String v = ConfigManager.getPhoneNumber();
                            if (v != null) param.setResult(v);
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        // Hook network operator methods (MCC/MNC)
        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getNetworkOperator",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String mccMnc = ConfigManager.getSystemProperty("gsm.operator.numeric", null);
                            if (mccMnc != null) {
                                param.setResult(mccMnc);
                            }
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getNetworkOperatorName",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String operatorName = ConfigManager.getSystemProperty("gsm.operator.alpha", null);
                            if (operatorName != null) {
                                param.setResult(operatorName);
                            }
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getSimOperator",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String simMccMnc = ConfigManager.getSystemProperty("gsm.sim.operator.numeric", null);
                            if (simMccMnc != null) {
                                param.setResult(simMccMnc);
                            }
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getSimOperatorName",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String simOperatorName = ConfigManager.getSystemProperty("gsm.sim.operator.alpha", null);
                            if (simOperatorName != null) {
                                param.setResult(simOperatorName);
                            }
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getSimCountryIso",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String simCountry = ConfigManager.getSystemProperty("gsm.sim.operator.iso-country", null);
                            if (simCountry != null) {
                                param.setResult(simCountry);
                            }
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(telephonyManager, "getNetworkCountryIso",
                    new ZygiskMethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String networkCountry = ConfigManager.getSystemProperty("gsm.operator.iso-country", null);
                            if (networkCountry != null) {
                                param.setResult(networkCountry);
                            }
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }

        // Android 13+ replaced TelephonyManager.getLine1Number() with
        // SubscriptionManager.getPhoneNumber(int) and getPhoneNumber(int, int).
        Class<?> subscriptionManager = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.telephony.SubscriptionManager", classLoader);
        if (subscriptionManager != null) {
            ZygiskMethodHook phoneNumberHook = new ZygiskMethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String v = ConfigManager.getPhoneNumber();
                    if (v != null) param.setResult(v);
                }
            };
            try {
                LSPlantJavaWrapper.findAndHookMethod(subscriptionManager, "getPhoneNumber",
                        int.class, phoneNumberHook);
            } catch (NoSuchMethodError ignored) {
            }
            try {
                LSPlantJavaWrapper.findAndHookMethod(subscriptionManager, "getPhoneNumber",
                        int.class, int.class, phoneNumberHook);
            } catch (NoSuchMethodError ignored) {
            }
        }
    }



}
