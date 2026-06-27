package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.utils.ConfigManager;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class MediaDrmHooks {

    private static final String DEVICE_UNIQUE_ID = "deviceUniqueId";

    public static void hook(LoadPackageParam lpparam) {
        Class<?> mediaDrmClass = XposedHelpers.findClassIfExists(
                "android.media.MediaDrm",
                lpparam.classLoader
        );

        if (mediaDrmClass == null) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(mediaDrmClass, "getPropertyByteArray",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String propertyName = (String) param.args[0];

                            if (DEVICE_UNIQUE_ID.equals(propertyName)) {
                                byte[] v = ConfigManager.getMediaDrmId();
                                if (v != null) param.setResult(v);
                            }
                        }
                    });
        } catch (NoSuchMethodError ignored) {
        }
    }
}
