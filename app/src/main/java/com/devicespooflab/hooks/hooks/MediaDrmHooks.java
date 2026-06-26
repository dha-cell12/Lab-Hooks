package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.LSPlantJavaWrapper;
import com.devicespooflab.hooks.ZygiskMethodHook;

import com.devicespooflab.hooks.utils.ConfigManager;





public class MediaDrmHooks {

    private static final String DEVICE_UNIQUE_ID = "deviceUniqueId";

    public static void hook(ClassLoader classLoader, String processName) {
        Class<?> mediaDrmClass = com.devicespooflab.hooks.ZygiskEntry.findClass(
                "android.media.MediaDrm",
                classLoader
        );

        if (mediaDrmClass == null) {
            return;
        }

        try {
            LSPlantJavaWrapper.findAndHookMethod(mediaDrmClass, "getPropertyByteArray",
                    String.class,
                    new ZygiskMethodHook() {
                        @Override
                        public void afterHookedMethod(MethodHookParam param) {
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
