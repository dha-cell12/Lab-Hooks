package com.devicespooflab.hooks.utils;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public class ZygiskConfigBridge {
    private static final String TAG = "DeviceSpoofLab-ZygiskConfig";
    private static final String CONFIG_PATH = "/data/adb/devicespooflab/config.prop";

    public static void saveConfig(Map<String, String> props) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        File tempFile = new File("/data/local/tmp/devicespooflab_config.prop");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(sb.toString().getBytes());
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write temp config", e);
            return;
        }

        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.OutputStream os = process.getOutputStream();
            os.write(("mkdir -p /data/adb/devicespooflab\n").getBytes());
            os.write(("mv " + tempFile.getAbsolutePath() + " " + CONFIG_PATH + "\n").getBytes());
            os.write(("chmod 644 " + CONFIG_PATH + "\n").getBytes());
            os.write("exit\n".getBytes());
            os.flush();
            process.waitFor();
            Log.i(TAG, "Config saved to " + CONFIG_PATH + " via mv as root");
        } catch (Exception e) {
            Log.e(TAG, "Failed to move config to /data/adb", e);
        }
    }
}
