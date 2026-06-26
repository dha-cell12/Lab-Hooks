package com.devicespooflab.hooks.utils;

import com.devicespooflab.hooks.ui.IdentifierRegistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private static final String DEFAULT_CPUINFO_FEATURES =
            "fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp"
            + " cpuid asimdrdm jscvt fcma lrcpc dcpop sha3 sm3 sm4 asimddp sha512 sve"
            + " asimdfhm dit uscat ilrcpc flagm ssbs sb paca pacg dcpodp sve2 sveaes"
            + " svepmull svebitperm svesha3 svesm4 flagm2 frint svei8mm svebf16 i8mm"
            + " bf16 dgh bti";

    private static final String DEFAULT_CPUINFO_PARTS =
            "0xd05,0xd05,0xd05,0xd05,0xd41,0xd41,0xd44,0xd44";
    private static final String DEFAULT_CPUINFO_VARIANTS =
            "0x1,0x1,0x1,0x1,0x1,0x1,0x1,0x1";
    private static final String DEFAULT_CPUINFO_REVISIONS =
            "0,0,0,0,0,0,0,0";

    private static final String[] CONFIG_PATHS = {
        "/data/data/com.devicespooflab.hooks/files/device_profile.conf",
    };

    private static volatile Map<String, String> allProperties = null;

    private static volatile String cachedIMEI = null;
    private static volatile String cachedMEID = null;
    private static volatile String cachedIMSI = null;
    private static volatile String cachedICCID = null;
    private static volatile String cachedPhoneNumber = null;
    private static volatile String cachedSerial = null;
    private static volatile String cachedBootloader = null;
    private static volatile String cachedBuildId = null;
    private static volatile String cachedBuildFingerprint = null;
    private static volatile String cachedBuildIncremental = null;
    private static volatile String cachedGAID = null;
    private static volatile String cachedGSFId = null;
    private static volatile String cachedAndroidId = null;
    private static volatile byte[] cachedMediaDrmId = null;
    private static volatile String cachedAppSetId = null;

    public static synchronized void init() {
        // Idempotent: LSPosed calls handleLoadPackage once per package loaded
        // in a process. Re-running init would clobber a live config that the
        // Application.attach hook has already pulled from RemotePreferences.
        if (allProperties != null) return;
        Map<String, String> loaded = readXSharedPreferences();
        if (loaded == null || loaded.isEmpty()) {
            loaded = readConfigFile();
        }
        Map<String, String> defaults = getEmbeddedDefaults();
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            if (!loaded.containsKey(e.getKey())) {
                loaded.put(e.getKey(), e.getValue());
            }
        }
        resetCaches();
        allProperties = Collections.unmodifiableMap(new HashMap<>(loaded));
    }

    private static Map<String, String> readXSharedPreferences() {
        // XSharedPreferences is no longer used in Zygisk mode.
        return null;
    }

    public static Map<String, String> getRawProperties() {
        Map<String, String> props = allProperties;
        if (props == null) {
            init();
            props = allProperties;
        }
        return props;
    }

    public static boolean isOwnPackageProcess(String processName) {
        if (processName == null) return false;
        return processName.equals("com.devicespooflab.hooks")
                || processName.startsWith("com.devicespooflab.hooks:");
    }

    public static synchronized boolean loadFromRemotePreferences() {
        // RemotePreferences is no longer used in Zygisk mode.
        return false;
    }

    public static synchronized boolean publishToRemotePreferences() {
        // RemotePreferences is no longer used in Zygisk mode.
        return false;
    }

    // Cross-process freshness marker stamped on each publish; target processes
    // compare it against their in-memory copy to detect new edits.
    private static final String REMOTE_GENERATION_KEY = "_generation";

    public static String getLocalRemoteGeneration() {
        Map<String, String> props = allProperties;
        if (props == null) return null;
        return props.get(REMOTE_GENERATION_KEY);
    }

    public static boolean refreshFromRemoteIfNewer(ClassLoader loader) {
        android.content.SharedPreferences prefs =
                XposedServiceBridge.getRemotePreferencesFresh("config");
        if (prefs == null) return false;
        String remoteGen;
        try {
            remoteGen = prefs.getString(REMOTE_GENERATION_KEY, null);
        } catch (Throwable t) {
            return false;
        }
        if (remoteGen == null || remoteGen.isEmpty()) return false;
        String localGen = getLocalRemoteGeneration();
        if (remoteGen.equals(localGen)) return false;
        boolean reloaded = loadFromRemotePreferences();
        if (reloaded) {
            if (loader != null) {
                try {
                    com.devicespooflab.hooks.hooks.BuildHooks.refreshStaticFields(loader);
                } catch (Throwable t) {
                    android.util.Log.w("DeviceSpoofLab",
                            "BuildHooks.refreshStaticFields during refresh failed: "
                                    + t.getMessage());
                }
            }
            android.util.Log.i("DeviceSpoofLab",
                    "Remote config refreshed: " + localGen + " -> " + remoteGen);
        }
        return reloaded;
    }

    private static void resetCaches() {
        cachedIMEI = null;
        cachedMEID = null;
        cachedIMSI = null;
        cachedICCID = null;
        cachedPhoneNumber = null;
        cachedSerial = null;
        cachedBootloader = null;
        cachedBuildId = null;
        cachedBuildFingerprint = null;
        cachedBuildIncremental = null;
        cachedGAID = null;
        cachedGSFId = null;
        cachedAndroidId = null;
        cachedMediaDrmId = null;
        cachedAppSetId = null;
        cachedWifiMac = null;
        cachedBssid = null;
        cachedBluetoothMac = null;
        cachedEid = null;
    }

    private static Map<String, String> readConfigFile() {
        Map<String, String> config = new HashMap<>();

        for (String configPath : CONFIG_PATHS) {
            File configFile = new File(configPath);
            boolean exists = configFile.exists();
            boolean canRead = exists && configFile.canRead();
            android.util.Log.i("DeviceSpoofLab",
                    "config path=" + configPath + " exists=" + exists + " canRead=" + canRead);
            if (exists && canRead) {
                try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        int equalIndex = line.indexOf('=');
                        if (equalIndex > 0) {
                            String key = line.substring(0, equalIndex).trim();
                            String value = line.substring(equalIndex + 1).trim();

                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }

                            config.put(key, value);
                        }
                    }
                    android.util.Log.i("DeviceSpoofLab",
                            "config loaded from " + configPath + " entries=" + config.size());
                    return config;
                } catch (Exception e) {
                    android.util.Log.w("DeviceSpoofLab",
                            "config read failed " + configPath + ": " + e.getMessage());
                }
            }
        }

        android.util.Log.i("DeviceSpoofLab", "config falling back to embedded defaults");
        return getEmbeddedDefaults();
    }

    private static Map<String, String> getEmbeddedDefaults() {
        Map<String, String> defaults = new HashMap<>();

        defaults.put("ro.product.brand", "google");
        defaults.put("ro.product.manufacturer", "Google");
        defaults.put("ro.product.model", "Pixel 7 Pro");
        defaults.put("ro.product.name", "cheetah");
        defaults.put("ro.product.device", "cheetah");
        defaults.put("ro.product.board", "cheetah");
        defaults.put("ro.hardware", "cheetah");
        defaults.put("ro.board.platform", "gs201");

        String[] partitions = {"product", "system", "system_ext", "vendor", "vendor_dlkm", "odm", "bootimage", "system_dlkm"};
        for (String partition : partitions) {
            defaults.put("ro.product." + partition + ".brand", "google");
            defaults.put("ro.product." + partition + ".manufacturer", "Google");
            defaults.put("ro.product." + partition + ".model", "Pixel 7 Pro");
            defaults.put("ro.product." + partition + ".name", "cheetah");
            defaults.put("ro.product." + partition + ".device", "cheetah");
        }

        defaults.put("ro.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.build.id", "BP4A.250605.009");
        defaults.put("ro.build.display.id", "BP4A.250605.009");
        defaults.put("ro.build.version.incremental", "12621605");
        defaults.put("build.id.prefix", "BP4A");
        defaults.put("ro.build.type", "user");
        defaults.put("ro.build.tags", "release-keys");
        defaults.put("ro.build.description", "cheetah-user 16 BP4A.250605.009 12621605 release-keys");
        defaults.put("ro.build.product", "cheetah");
        defaults.put("ro.build.device", "cheetah");
        defaults.put("ro.build.characteristics", "nosdcard");
        defaults.put("ro.build.flavor", "cheetah-user");

        defaults.put("ro.build.version.release", "16");
        defaults.put("ro.build.version.release_or_codename", "16");
        defaults.put("ro.build.version.release_or_preview_display", "16");
        defaults.put("ro.build.version.sdk", "36");
        defaults.put("ro.build.version.codename", "REL");
        defaults.put("ro.build.version.security_patch", "2025-06-05");

        defaults.put("ro.product.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.product.build.id", "BP4A.250605.009");
        defaults.put("ro.product.build.tags", "release-keys");
        defaults.put("ro.product.build.type", "user");
        defaults.put("ro.product.build.version.incremental", "12621605");
        defaults.put("ro.product.build.version.release", "16");
        defaults.put("ro.product.build.version.release_or_codename", "16");
        defaults.put("ro.product.build.version.sdk", "36");

        defaults.put("ro.system.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.system_ext.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.vendor.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.odm.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.bootimage.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.system_dlkm.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");
        defaults.put("ro.vendor_dlkm.build.fingerprint", "google/cheetah/cheetah:16/BP4A.250605.009/12621605:user/release-keys");

        defaults.put("ro.vendor.build.version.release", "16");
        defaults.put("ro.vendor.build.version.release_or_codename", "16");
        defaults.put("ro.vendor_dlkm.build.version.release", "16");
        defaults.put("ro.vendor_dlkm.build.version.release_or_codename", "16");
        defaults.put("ro.odm.build.version.release", "16");
        defaults.put("ro.odm.build.version.release_or_codename", "16");
        defaults.put("ro.bootimage.build.version.release", "16");
        defaults.put("ro.bootimage.build.version.release_or_codename", "16");
        defaults.put("ro.system_dlkm.build.version.release", "16");
        defaults.put("ro.system_dlkm.build.version.release_or_codename", "16");

        defaults.put("ro.debuggable", "0");
        defaults.put("ro.secure", "1");
        defaults.put("ro.adb.secure", "1");
        defaults.put("ro.build.selinux", "0");
        defaults.put("ro.boot.verifiedbootstate", "green");
        defaults.put("ro.boot.flash.locked", "1");
        defaults.put("ro.boot.vbmeta.device_state", "locked");
        defaults.put("ro.boot.warranty_bit", "0");
        defaults.put("sys.oem_unlock_allowed", "0");
        defaults.put("ro.boot.veritymode", "enforcing");
        defaults.put("ro.crypto.state", "encrypted");
        defaults.put("ro.kernel.qemu", "0");
        defaults.put("ro.boot.qemu", "0");

        defaults.put("ro.boot.qemu.avd_name", "");
        defaults.put("ro.boot.qemu.camera_hq_edge_processing", "0");
        defaults.put("ro.boot.qemu.camera_protocol_ver", "0");
        defaults.put("ro.boot.qemu.cpuvulkan.version", "0");
        defaults.put("ro.boot.qemu.gltransport.drawFlushInterval", "0");
        defaults.put("ro.boot.qemu.gltransport.name", "");
        defaults.put("ro.boot.qemu.hwcodec.avcdec", "0");
        defaults.put("ro.boot.qemu.hwcodec.hevcdec", "0");
        defaults.put("ro.boot.qemu.hwcodec.vpxdec", "0");
        defaults.put("ro.boot.qemu.settings.system.screen_off_timeout", "0");
        defaults.put("ro.boot.qemu.virtiowifi", "0");
        defaults.put("ro.boot.qemu.vsync", "0");

        defaults.put("ro.boot.hardware", "cheetah");
        defaults.put("ro.boot.hardware.vulkan", "mali");
        defaults.put("ro.boot.hardware.gltransport", "");
        defaults.put("ro.boot.mode", "normal");
        defaults.put("ro.product.cpu.abi", "arm64-v8a");
        defaults.put("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a,armeabi");
        defaults.put("ro.product.cpu.abilist64", "arm64-v8a");
        defaults.put("ro.product.cpu.abilist32", "armeabi-v7a,armeabi");
        defaults.put("ro.arch", "arm64");
        defaults.put("ro.sf.lcd_density", "512");
        defaults.put("ro.treble.enabled", "true");

        defaults.put("hardware.cpu.cores", "8");
        defaults.put("cpuinfo.cores", "8");
        defaults.put("cpuinfo.bogomips", "38.40");
        defaults.put("cpuinfo.features", DEFAULT_CPUINFO_FEATURES);
        defaults.put("cpuinfo.implementer", "0x41");
        defaults.put("cpuinfo.architecture", "8");
        defaults.put("cpuinfo.variants", DEFAULT_CPUINFO_VARIANTS);
        defaults.put("cpuinfo.parts", DEFAULT_CPUINFO_PARTS);
        defaults.put("cpuinfo.revisions", DEFAULT_CPUINFO_REVISIONS);
        defaults.put("cpuinfo.hardware", "Cheetah");
        defaults.put("cpuinfo.revision", "0001");

        defaults.put("ro.hardware.vulkan", "mali");
        defaults.put("ro.hardware.gralloc", "gs201");
        defaults.put("ro.hardware.power", "gs201-power");
        defaults.put("ro.hardware.egl", "mali");
        defaults.put("ro.soc.model", "gs201");
        defaults.put("ro.soc.manufacturer", "Google");

        defaults.put("screen.width", "1440");
        defaults.put("screen.height", "3120");
        defaults.put("screen.density", "512");

        defaults.put("memory.total_kb", "12582912");
        defaults.put("memory.available_kb", "10485760");
        defaults.put("memory.class_mb", "512");
        defaults.put("memory.large_class_mb", "1024");
        defaults.put("native_heap.scale", "4");
        defaults.put("dalvik.vm.heapsize", "576m");
        defaults.put("dalvik.vm.heapgrowthlimit", "256m");
        defaults.put("dalvik.vm.heapmaxfree", "8m");
        defaults.put("dalvik.vm.heapminfree", "512k");
        defaults.put("dalvik.vm.heapstartsize", "8m");
        defaults.put("dalvik.vm.heaptargetutilization", "0.75");

        defaults.put("gsm.operator.alpha", "T-Mobile");
        defaults.put("gsm.operator.numeric", "310260");
        defaults.put("gsm.sim.operator.alpha", "T-Mobile");
        defaults.put("gsm.sim.operator.numeric", "310260");
        defaults.put("gsm.sim.operator.iso-country", "us");
        // Empty = passthrough; MainActivity seeds a concrete TZ on first launch.
        defaults.put("persist.sys.timezone", "");
        defaults.put("persist.sys.usb.config", "none");

        defaults.put("webview.user_agent", "Mozilla/5.0 (Linux; Android 16; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");

        // Blank means generate or derive at runtime.
        defaults.put("SERIAL_NUMBER", "");
        defaults.put("ro.serialno", "");
        defaults.put("ro.boot.serialno", "");
        defaults.put("ro.bootloader", "");
        defaults.put("bootloader.prefix", "cheetah-1.2");
        defaults.put("ANDROID_ID", "");

        defaults.put("gpu.vendor", "ARM");
        defaults.put("gpu.renderer", "Mali-G710 MC10");
        defaults.put("gpu.unmasked_vendor", "ARM");
        defaults.put("gpu.unmasked_renderer", "Mali-G710 MC10");

        defaults.put("wifi.mac", "");
        defaults.put("wifi.bssid", "");
        defaults.put("wifi.ssid", "");
        defaults.put("bluetooth.mac", "");
        defaults.put("bluetooth.name", "");

        defaults.put("battery.capacity_uah", "5050000");
        defaults.put("battery.charge_counter_uah", "3800000");
        defaults.put("battery.energy_counter_nwh", "14250000000");

        defaults.put("storage.total_bytes", "137438953472");
        defaults.put("storage.available_bytes", "84296499200");

        defaults.put("kernel.osrelease", "5.10.157-android13-4-00006-g1234567-ab12345");
        defaults.put("kernel.version", "#1 SMP PREEMPT Tue Dec  3 21:01:46 UTC 2024");
        defaults.put("kernel.hostname", "localhost");

        defaults.put("locale.language", "en");
        defaults.put("locale.country", "US");

        defaults.put("install.installer_package", "com.android.vending");
        defaults.put("install.initiating_package", "com.android.vending");

        defaults.put("fingerprint.seed", "");

        defaults.put("hooks.hide_accounts", "0");
        defaults.put("debug.verbose", "0");

        // 1 = spoof, 0 = passthrough.
        defaults.put("enabled.imei", "1");
        defaults.put("enabled.meid", "1");
        defaults.put("enabled.imsi", "1");
        defaults.put("enabled.iccid", "1");
        defaults.put("enabled.phone_number", "1");
        defaults.put("enabled.serial", "1");
        defaults.put("enabled.android_id", "1");
        defaults.put("enabled.gsf_id", "1");
        defaults.put("enabled.bootloader", "1");
        defaults.put("enabled.gaid", "1");
        defaults.put("enabled.app_set_id", "1");
        defaults.put("enabled.media_drm_id", "1");
        defaults.put("enabled.wifi_mac", "1");
        defaults.put("enabled.wifi_bssid", "1");
        defaults.put("enabled.bluetooth_mac", "1");
        defaults.put("enabled.eid", "1");
        defaults.put("enabled.build_fingerprint", "1");
        defaults.put("enabled.build_id", "1");
        defaults.put("enabled.build_incremental", "1");
        defaults.put("enabled.security_patch", "1");
        defaults.put("enabled.fingerprint_seed", "1");

        // Blank slots are generated on first read; the UI persists them.
        defaults.put("identifier.imei", "");
        defaults.put("identifier.meid", "");
        defaults.put("identifier.imsi", "");
        defaults.put("identifier.iccid", "");
        defaults.put("identifier.phone_number", "");
        defaults.put("identifier.gsf_id", "");
        defaults.put("identifier.gaid", "");
        defaults.put("identifier.app_set_id", "");
        defaults.put("identifier.media_drm_id", "");
        defaults.put("euicc.eid", "");

        return defaults;
    }

    public static String getDefaultConfigText() {
        return formatConfigText(getEmbeddedDefaults());
    }

    private static String formatConfigText(Map<String, String> defaults) {
        StringBuilder sb = new StringBuilder(12000);
        Set<String> emitted = new LinkedHashSet<>();

        sb.append("# DeviceSpoofLab-Hooks Auto-Generated Config\n");
        sb.append("# Default profile: Google Pixel 7 Pro (Android 16)\n");
        sb.append("# Edit values here to spoof a different Android device profile.\n");
        sb.append("# Blank identifier fields are generated or derived at runtime.\n\n");

        appendConfigBlock(sb, defaults, emitted, "Per-identifier toggles (1=spoof, 0=passthrough)",
                "enabled.imei", "enabled.meid", "enabled.imsi", "enabled.iccid",
                "enabled.phone_number", "enabled.serial", "enabled.android_id",
                "enabled.gsf_id", "enabled.bootloader", "enabled.gaid",
                "enabled.app_set_id", "enabled.media_drm_id", "enabled.wifi_mac",
                "enabled.wifi_bssid", "enabled.bluetooth_mac", "enabled.eid",
                "enabled.build_fingerprint", "enabled.build_id",
                "enabled.build_incremental", "enabled.security_patch",
                "enabled.fingerprint_seed");

        appendConfigBlock(sb, defaults, emitted, "Persisted random identifier values (blank = generate at runtime)",
                "identifier.imei", "identifier.meid", "identifier.imsi", "identifier.iccid",
                "identifier.phone_number", "identifier.gsf_id", "identifier.gaid",
                "identifier.app_set_id", "identifier.media_drm_id", "euicc.eid");

        appendConfigBlock(sb, defaults, emitted, "Device identity",
                "ro.product.brand", "ro.product.manufacturer", "ro.product.model",
                "ro.product.name", "ro.product.device", "ro.product.board",
                "ro.hardware", "ro.board.platform", "ro.soc.manufacturer", "ro.soc.model");

        appendConfigBlock(sb, defaults, emitted, "Partition identity",
                "ro.product.product.brand", "ro.product.product.manufacturer",
                "ro.product.product.model", "ro.product.product.name", "ro.product.product.device",
                "ro.product.system.brand", "ro.product.system.manufacturer",
                "ro.product.system.model", "ro.product.system.name", "ro.product.system.device",
                "ro.product.system_ext.brand", "ro.product.system_ext.manufacturer",
                "ro.product.system_ext.model", "ro.product.system_ext.name", "ro.product.system_ext.device",
                "ro.product.vendor.brand", "ro.product.vendor.manufacturer",
                "ro.product.vendor.model", "ro.product.vendor.name", "ro.product.vendor.device",
                "ro.product.vendor_dlkm.brand", "ro.product.vendor_dlkm.manufacturer",
                "ro.product.vendor_dlkm.model", "ro.product.vendor_dlkm.name", "ro.product.vendor_dlkm.device",
                "ro.product.odm.brand", "ro.product.odm.manufacturer",
                "ro.product.odm.model", "ro.product.odm.name", "ro.product.odm.device",
                "ro.product.bootimage.brand", "ro.product.bootimage.manufacturer",
                "ro.product.bootimage.model", "ro.product.bootimage.name", "ro.product.bootimage.device",
                "ro.product.system_dlkm.brand", "ro.product.system_dlkm.manufacturer",
                "ro.product.system_dlkm.model", "ro.product.system_dlkm.name", "ro.product.system_dlkm.device");

        appendConfigBlock(sb, defaults, emitted, "Build information",
                "ro.build.fingerprint", "ro.build.id", "ro.build.display.id",
                "build.id.prefix", "ro.build.version.incremental", "ro.build.type",
                "ro.build.tags", "ro.build.description", "ro.build.product",
                "ro.build.device", "ro.build.characteristics", "ro.build.flavor",
                "ro.build.version.release", "ro.build.version.release_or_codename",
                "ro.build.version.release_or_preview_display", "ro.build.version.sdk",
                "ro.build.version.codename", "ro.build.version.security_patch");

        appendConfigBlock(sb, defaults, emitted, "Partition build information",
                "ro.product.build.fingerprint", "ro.product.build.id",
                "ro.product.build.tags", "ro.product.build.type",
                "ro.product.build.version.incremental", "ro.product.build.version.release",
                "ro.product.build.version.release_or_codename", "ro.product.build.version.sdk",
                "ro.system.build.fingerprint", "ro.system_ext.build.fingerprint",
                "ro.vendor.build.fingerprint", "ro.odm.build.fingerprint",
                "ro.bootimage.build.fingerprint", "ro.system_dlkm.build.fingerprint",
                "ro.vendor_dlkm.build.fingerprint", "ro.vendor.build.version.release",
                "ro.vendor.build.version.release_or_codename",
                "ro.vendor_dlkm.build.version.release",
                "ro.vendor_dlkm.build.version.release_or_codename",
                "ro.odm.build.version.release", "ro.odm.build.version.release_or_codename",
                "ro.bootimage.build.version.release",
                "ro.bootimage.build.version.release_or_codename",
                "ro.system_dlkm.build.version.release",
                "ro.system_dlkm.build.version.release_or_codename");

        appendConfigBlock(sb, defaults, emitted, "Security and emulator denylists",
                "ro.debuggable", "ro.secure", "ro.adb.secure", "ro.build.selinux",
                "ro.boot.verifiedbootstate", "ro.boot.flash.locked",
                "ro.boot.vbmeta.device_state", "ro.boot.warranty_bit",
                "sys.oem_unlock_allowed", "ro.boot.veritymode", "ro.crypto.state",
                "ro.kernel.qemu", "ro.boot.qemu");

        appendConfigBlock(sb, defaults, emitted, "Hardware, CPU, memory, and display",
                "ro.boot.hardware", "ro.boot.hardware.vulkan", "ro.boot.hardware.gltransport",
                "ro.boot.mode", "ro.product.cpu.abi", "ro.product.cpu.abilist",
                "ro.product.cpu.abilist64", "ro.product.cpu.abilist32", "ro.arch",
                "ro.sf.lcd_density", "ro.treble.enabled", "ro.hardware.vulkan",
                "ro.hardware.gralloc", "ro.hardware.power", "ro.hardware.egl",
                "hardware.cpu.cores", "cpuinfo.cores", "cpuinfo.bogomips",
                "cpuinfo.features", "cpuinfo.implementer", "cpuinfo.architecture",
                "cpuinfo.variants", "cpuinfo.parts", "cpuinfo.revisions",
                "cpuinfo.hardware", "cpuinfo.revision", "memory.total_kb",
                "memory.available_kb", "memory.class_mb", "memory.large_class_mb",
                "native_heap.scale", "screen.width", "screen.height", "screen.density",
                "dalvik.vm.heapsize", "dalvik.vm.heapgrowthlimit",
                "dalvik.vm.heapmaxfree", "dalvik.vm.heapminfree",
                "dalvik.vm.heapstartsize", "dalvik.vm.heaptargetutilization");

        appendConfigBlock(sb, defaults, emitted, "Identifiers",
                "SERIAL_NUMBER", "ro.serialno", "ro.boot.serialno", "ro.bootloader",
                "bootloader.prefix", "ANDROID_ID", "fingerprint.seed");

        appendConfigBlock(sb, defaults, emitted, "Network, Bluetooth, WebView, and GPU",
                "wifi.mac", "wifi.bssid", "wifi.ssid", "bluetooth.mac",
                "bluetooth.name", "webview.user_agent", "gpu.vendor",
                "gpu.renderer", "gpu.unmasked_vendor", "gpu.unmasked_renderer");

        appendConfigBlock(sb, defaults, emitted, "Battery, storage, kernel, locale, and install metadata",
                "battery.capacity_uah", "battery.charge_counter_uah",
                "battery.energy_counter_nwh", "storage.total_bytes",
                "storage.available_bytes", "kernel.osrelease", "kernel.version",
                "kernel.hostname", "locale.language", "locale.country",
                "install.installer_package", "install.initiating_package");

        appendConfigBlock(sb, defaults, emitted, "Carrier and GSM",
                "gsm.operator.alpha", "gsm.operator.numeric", "gsm.sim.operator.alpha",
                "gsm.sim.operator.numeric", "gsm.sim.operator.iso-country",
                "persist.sys.timezone", "persist.sys.usb.config");

        appendConfigBlock(sb, defaults, emitted, "Behavior flags",
                "hooks.hide_accounts", "debug.verbose");

        List<String> remaining = new ArrayList<>();
        for (String key : defaults.keySet()) {
            if (!emitted.contains(key)) {
                remaining.add(key);
            }
        }
        Collections.sort(remaining);
        if (!remaining.isEmpty()) {
            appendConfigBlock(sb, defaults, emitted, "Additional properties",
                    remaining.toArray(new String[0]));
        }

        return sb.toString();
    }

    private static void appendConfigBlock(StringBuilder sb, Map<String, String> defaults,
                                          Set<String> emitted, String title, String... keys) {
        sb.append("# ").append(title).append('\n');
        for (String key : keys) {
            if (!defaults.containsKey(key)) {
                continue;
            }
            sb.append(key).append('=').append(defaults.get(key)).append('\n');
            emitted.add(key);
        }
        sb.append('\n');
    }

    private static String getConfigValue(String key) {
        Map<String, String> props = allProperties;
        if (props == null) {
            init();
            props = allProperties;
        }
        return props.get(key);
    }

    private static boolean hasConfigValue(String key) {
        String value = getConfigValue(key);
        return value != null && !value.isEmpty();
    }

    public static String getSystemProperty(String key, String defaultValue) {
        String identifier = IdentifierRegistry.identifierForKey(key);
        if (identifier != null && !isIdentifierEnabled(identifier)) {
            return defaultValue;
        }
        String value = getConfigValue(key);

        if ("ro.serialno".equals(key) || "ro.boot.serialno".equals(key)) {
            String resolved = (value == null || value.isEmpty()) ? getSerial() : value;
            return resolved != null ? resolved : defaultValue;
        }

        if ("ro.bootloader".equals(key)) {
            String resolved = (value == null || value.isEmpty()) ? getBuildBootloader() : value;
            return resolved != null ? resolved : defaultValue;
        }

        if ("ro.build.fingerprint".equals(key)) {
            String resolved = (value == null || value.isEmpty()) ? getBuildFingerprint() : value;
            return resolved != null ? resolved : defaultValue;
        }

        if ("ro.build.id".equals(key) || "ro.build.display.id".equals(key)) {
            String resolved = (value == null || value.isEmpty()) ? getBuildId() : value;
            return resolved != null ? resolved : defaultValue;
        }

        if ("ro.build.version.incremental".equals(key)) {
            String resolved = (value == null || value.isEmpty()) ? getBuildVersionIncremental() : value;
            return resolved != null ? resolved : defaultValue;
        }

        // Empty = passthrough; special-cased keys above handle empty-as-generate.
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public static synchronized String getIMEI() {
        if (!isIdentifierEnabled("imei")) return null;
        if (cachedIMEI == null) {
            String configured = getConfigValue("identifier.imei");
            cachedIMEI = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateIMEI();
        }
        return cachedIMEI;
    }

    public static synchronized String getMEID() {
        if (!isIdentifierEnabled("meid")) return null;
        if (cachedMEID == null) {
            String configured = getConfigValue("identifier.meid");
            cachedMEID = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateMEID();
        }
        return cachedMEID;
    }

    public static synchronized String getIMSI() {
        if (!isIdentifierEnabled("imsi")) return null;
        if (cachedIMSI == null) {
            String configured = getConfigValue("identifier.imsi");
            cachedIMSI = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateIMSI();
        }
        return cachedIMSI;
    }

    public static synchronized String getICCID() {
        if (!isIdentifierEnabled("iccid")) return null;
        if (cachedICCID == null) {
            String configured = getConfigValue("identifier.iccid");
            cachedICCID = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateICCID();
        }
        return cachedICCID;
    }

    public static synchronized String getPhoneNumber() {
        if (!isIdentifierEnabled("phone_number")) return null;
        if (cachedPhoneNumber == null) {
            String configured = getConfigValue("identifier.phone_number");
            cachedPhoneNumber = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generatePhoneNumber();
        }
        return cachedPhoneNumber;
    }

    public static synchronized String getSerial() {
        if (!isIdentifierEnabled("serial")) return null;
        if (cachedSerial == null) {
            if (hasConfigValue("SERIAL_NUMBER")) {
                cachedSerial = getConfigValue("SERIAL_NUMBER");
            } else if (hasConfigValue("ro.serialno")) {
                cachedSerial = getConfigValue("ro.serialno");
            } else if (hasConfigValue("ro.boot.serialno")) {
                cachedSerial = getConfigValue("ro.boot.serialno");
            } else {
                cachedSerial = RandomGenerator.generateSerial();
            }
        }
        return cachedSerial;
    }

    public static synchronized String getGAID() {
        if (!isIdentifierEnabled("gaid")) return null;
        if (cachedGAID == null) {
            String configured = getConfigValue("identifier.gaid");
            cachedGAID = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateGAID();
        }
        return cachedGAID;
    }

    public static synchronized String getGSFId() {
        if (!isIdentifierEnabled("gsf_id")) return null;
        if (cachedGSFId == null) {
            String configured = getConfigValue("identifier.gsf_id");
            cachedGSFId = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateGSFId();
        }
        return cachedGSFId;
    }

    public static synchronized String getAndroidId() {
        if (!isIdentifierEnabled("android_id")) return null;
        if (cachedAndroidId == null) {
            if (hasConfigValue("ANDROID_ID")) {
                cachedAndroidId = getConfigValue("ANDROID_ID");
            } else {
                cachedAndroidId = RandomGenerator.generateAndroidId();
            }
        }
        return cachedAndroidId;
    }

    public static synchronized byte[] getMediaDrmId() {
        if (!isIdentifierEnabled("media_drm_id")) return null;
        if (cachedMediaDrmId == null) {
            String configured = getConfigValue("identifier.media_drm_id");
            if (configured != null && !configured.isEmpty()) {
                cachedMediaDrmId = hexToBytes(configured);
            }
            if (cachedMediaDrmId == null) {
                cachedMediaDrmId = RandomGenerator.generateMediaDrmId();
            }
        }
        return cachedMediaDrmId;
    }

    public static synchronized String getAppSetId() {
        if (!isIdentifierEnabled("app_set_id")) return null;
        if (cachedAppSetId == null) {
            String configured = getConfigValue("identifier.app_set_id");
            cachedAppSetId = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateGAID();
        }
        return cachedAppSetId;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        int len = hex.length();
        if ((len & 1) != 0) return null;
        byte[] out = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                int hi = Character.digit(hex.charAt(i), 16);
                int lo = Character.digit(hex.charAt(i + 1), 16);
                if (hi < 0 || lo < 0) return null;
                out[i / 2] = (byte) ((hi << 4) | lo);
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }

    public static boolean isConfigAvailable() {
        Map<String, String> props = allProperties;
        if (props == null) {
            init();
            props = allProperties;
        }
        return !props.isEmpty();
    }

    public static HashMap<String, String> getAllSpoofedProperties() {
        Map<String, String> props = allProperties;
        if (props == null) {
            init();
            props = allProperties;
        }
        HashMap<String, String> out = new HashMap<>(props.size() + 8);
        for (Map.Entry<String, String> e : props.entrySet()) {
            String key = e.getKey();
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            if (key.startsWith("enabled.") || key.startsWith("identifier.")) continue;
            String identifier = IdentifierRegistry.identifierForKey(key);
            if (identifier != null && !isIdentifierEnabled(identifier)) continue;
            out.put(key, v);
        }

        // Mirror getSystemProperty's special-cased keys so native sees the same answer.
        String serial = getSerial();
        if (serial != null && !serial.isEmpty()) {
            out.put("ro.serialno", serial);
            out.put("ro.boot.serialno", serial);
        }
        putIfNonEmpty(out, "ro.bootloader", getBuildBootloader());
        putIfNonEmpty(out, "ro.build.fingerprint", getBuildFingerprint());
        putIfNonEmpty(out, "ro.build.id", getBuildId());
        putIfNonEmpty(out, "ro.build.display.id", getBuildDisplay());
        putIfNonEmpty(out, "ro.build.version.incremental", getBuildVersionIncremental());

        putIfNonEmpty(out, "wifi.mac", getWifiMacAddress());
        putIfNonEmpty(out, "bluetooth.mac", getBluetoothMacAddress());

        out.put("kernel.osrelease", getKernelOsRelease());
        out.put("kernel.version", getKernelVersion());
        out.put("kernel.hostname", getKernelHostname());

        return out;
    }

    private static void putIfNonEmpty(Map<String, String> out, String key, String value) {
        if (value != null && !value.isEmpty()) {
            out.put(key, value);
        }
    }

    public static synchronized String getBuildFingerprint() {
        if (!isIdentifierEnabled("build_fingerprint")) return null;
        String fingerprint = getConfigValue("ro.build.fingerprint");
        if (fingerprint != null && !fingerprint.isEmpty()) {
            return fingerprint;
        }
        if (cachedBuildFingerprint == null) {
            cachedBuildFingerprint = RandomGenerator.generateFingerprint(
                    getBuildBrand(),
                    getBuildProduct(),
                    getBuildDevice(),
                    getBuildVersionRelease(),
                    getBuildId(),
                    getBuildVersionIncremental(),
                    getBuildType(),
                    getBuildTags()
            );
        }
        return cachedBuildFingerprint;
    }

    public static String getBuildModel() {
        return getConfigValue("ro.product.model");
    }

    public static String getBuildDevice() {
        return getConfigValue("ro.product.device");
    }

    public static String getBuildManufacturer() {
        return getConfigValue("ro.product.manufacturer");
    }

    public static String getBuildBrand() {
        return getConfigValue("ro.product.brand");
    }

    public static String getBuildProduct() {
        return getConfigValue("ro.product.name");
    }

    public static String getBuildBoard() {
        return getConfigValue("ro.product.board");
    }

    public static String getBuildHardware() {
        return getConfigValue("ro.hardware");
    }

    public static synchronized String getBuildBootloader() {
        if (!isIdentifierEnabled("bootloader")) return null;
        if (cachedBootloader == null) {
            String bootloader = getConfigValue("ro.bootloader");
            if (bootloader == null || bootloader.isEmpty()) {
                String prefix = propStringDef("bootloader.prefix", getBuildDevice());
                cachedBootloader = RandomGenerator.generateBootloader(prefix);
            } else {
                cachedBootloader = bootloader;
            }
        }
        return cachedBootloader;
    }

    public static synchronized String getBuildId() {
        if (!isIdentifierEnabled("build_id")) return null;
        String buildId = getConfigValue("ro.build.id");
        if (buildId != null && !buildId.isEmpty()) {
            return buildId;
        }
        if (cachedBuildId == null) {
            cachedBuildId = RandomGenerator.generateBuildId(
                    propStringDef("build.id.prefix", "AA1A")
            );
        }
        return cachedBuildId;
    }

    public static String getBuildDisplay() {
        if (!isIdentifierEnabled("build_id")) return null;
        String display = getConfigValue("ro.build.display.id");
        return (display != null && !display.isEmpty()) ? display : getBuildId();
    }

    public static String getBuildTags() {
        return getConfigValue("ro.build.tags");
    }

    public static String getBuildType() {
        return getConfigValue("ro.build.type");
    }

    public static String getBuildVersionRelease() {
        return getConfigValue("ro.build.version.release");
    }

    public static int getBuildVersionSdk() {
        String sdk = getConfigValue("ro.build.version.sdk");
        try {
            return Integer.parseInt(sdk);
        } catch (Exception e) {
            return 36;
        }
    }

    public static String getBuildVersionSecurityPatch() {
        if (!isIdentifierEnabled("security_patch")) return null;
        return getConfigValue("ro.build.version.security_patch");
    }

    public static synchronized String getBuildVersionIncremental() {
        if (!isIdentifierEnabled("build_incremental")) return null;
        String incremental = getConfigValue("ro.build.version.incremental");
        if (incremental != null && !incremental.isEmpty()) {
            return incremental;
        }
        if (cachedBuildIncremental == null) {
            cachedBuildIncremental = RandomGenerator.generateIncremental();
        }
        return cachedBuildIncremental;
    }

    public static String getBuildVersionCodename() {
        return getConfigValue("ro.build.version.codename");
    }

    public static String getBuildDescription() {
        return getConfigValue("ro.build.description");
    }

    public static String getBuildCharacteristics() {
        return getConfigValue("ro.build.characteristics");
    }

    public static String getBuildFlavor() {
        return getConfigValue("ro.build.flavor");
    }

    public static String getWebViewUserAgent() {
        return getConfigValue("webview.user_agent");
    }

    public static String getCpuAbi() {
        return getConfigValue("ro.product.cpu.abi");
    }

    public static String getCpuAbiList() {
        return getConfigValue("ro.product.cpu.abilist");
    }

    public static String getCpuAbiList64() {
        return getConfigValue("ro.product.cpu.abilist64");
    }

    public static String getCpuAbiList32() {
        return getConfigValue("ro.product.cpu.abilist32");
    }

    private static volatile String cachedWifiMac = null;
    private static volatile String cachedBssid = null;
    private static volatile String cachedBluetoothMac = null;
    private static volatile String cachedEid = null;

    public static synchronized String getWifiMacAddress() {
        if (!isIdentifierEnabled("wifi_mac")) return null;
        if (cachedWifiMac == null) {
            String configured = getConfigValue("wifi.mac");
            cachedWifiMac = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateMacAddress();
        }
        return cachedWifiMac;
    }

    public static synchronized String getWifiBssid() {
        if (!isIdentifierEnabled("wifi_bssid")) return null;
        if (cachedBssid == null) {
            String configured = getConfigValue("wifi.bssid");
            cachedBssid = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateBssid();
        }
        return cachedBssid;
    }

    public static String getWifiSsid() {
        String configured = getConfigValue("wifi.ssid");
        return (configured != null && !configured.isEmpty()) ? configured : "<unknown ssid>";
    }

    public static synchronized String getBluetoothMacAddress() {
        if (!isIdentifierEnabled("bluetooth_mac")) return null;
        if (cachedBluetoothMac == null) {
            String configured = getConfigValue("bluetooth.mac");
            cachedBluetoothMac = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateMacAddress();
        }
        return cachedBluetoothMac;
    }

    public static String getBluetoothName() {
        String configured = getConfigValue("bluetooth.name");
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        String model = getBuildModel();
        return (model != null && !model.isEmpty()) ? model : "Android Device";
    }

    public static boolean isHideAccountsEnabled() {
        return propBoolDef("hooks.hide_accounts", false);
    }

    public static boolean isVerboseLoggingEnabled() {
        return propBoolDef("debug.verbose", false);
    }

    public static synchronized String getEid() {
        if (!isIdentifierEnabled("eid")) return null;
        if (cachedEid == null) {
            String configured = getConfigValue("euicc.eid");
            cachedEid = (configured != null && !configured.isEmpty())
                    ? configured : RandomGenerator.generateEid();
        }
        return cachedEid;
    }

    public static int getScreenWidth() {
        return propIntDef("screen.width", 1440);
    }

    public static int getScreenHeight() {
        return propIntDef("screen.height", 3120);
    }

    public static int getScreenDensity() {
        return propIntDef("screen.density", 512);
    }

    public static int getCpuCoreCount() {
        return propIntDef("hardware.cpu.cores", propIntDef("cpuinfo.cores", 8));
    }

    public static String getCpuInfoBogoMips() {
        return propStringDef("cpuinfo.bogomips", "38.40");
    }

    public static String getCpuInfoFeatures() {
        return propStringDef("cpuinfo.features", DEFAULT_CPUINFO_FEATURES);
    }

    public static String getCpuInfoImplementer() {
        return propStringDef("cpuinfo.implementer", "0x41");
    }

    public static String getCpuInfoArchitecture() {
        return propStringDef("cpuinfo.architecture", "8");
    }

    public static String getCpuInfoVariants() {
        return propStringDef("cpuinfo.variants", DEFAULT_CPUINFO_VARIANTS);
    }

    public static String getCpuInfoParts() {
        return propStringDef("cpuinfo.parts", DEFAULT_CPUINFO_PARTS);
    }

    public static String getCpuInfoRevisions() {
        return propStringDef("cpuinfo.revisions", DEFAULT_CPUINFO_REVISIONS);
    }

    public static String getCpuInfoHardware() {
        String fallback = getBuildHardware();
        if (fallback == null || fallback.isEmpty()) {
            fallback = getBuildDevice();
        }
        return propStringDef("cpuinfo.hardware",
                fallback == null || fallback.isEmpty() ? "Android" : fallback);
    }

    public static String getCpuInfoRevision() {
        return propStringDef("cpuinfo.revision", "0001");
    }

    public static long getMemoryTotalKb() {
        return propLongDef("memory.total_kb", 12L * 1024L * 1024L);
    }

    public static long getMemoryAvailableKb() {
        return propLongDef("memory.available_kb",
                Math.max(0L, getMemoryTotalKb() - (2L * 1024L * 1024L)));
    }

    public static long getMemoryTotalBytes() {
        String bytes = getConfigValue("memory.total_bytes");
        if (bytes != null && !bytes.isEmpty()) {
            try { return Long.parseLong(bytes); } catch (NumberFormatException ignored) {}
        }
        return getMemoryTotalKb() * 1024L;
    }

    public static int getMemoryClassMb() {
        return propIntDef("memory.class_mb", 512);
    }

    public static int getLargeMemoryClassMb() {
        return propIntDef("memory.large_class_mb", 1024);
    }

    public static int getNativeHeapScale() {
        return propIntDef("native_heap.scale", 4);
    }

    public static long getBatteryCapacityUah() {
        return propLongDef("battery.capacity_uah", 5050000L);
    }

    public static long getBatteryChargeCounterUah() {
        return propLongDef("battery.charge_counter_uah", 3800000L);
    }

    public static long getBatteryEnergyCounterNwh() {
        return propLongDef("battery.energy_counter_nwh", 14250000000L);
    }

    public static long getStorageTotalBytes() {
        return propLongDef("storage.total_bytes", 137438953472L);
    }

    public static long getStorageAvailableBytes() {
        return propLongDef("storage.available_bytes", 84296499200L);
    }

    public static String getKernelOsRelease() {
        return propStringDef("kernel.osrelease",
                "5.10.157-android13-4-00006-g1234567-ab12345");
    }

    public static String getKernelVersion() {
        return propStringDef("kernel.version",
                "#1 SMP PREEMPT Tue Dec  3 21:01:46 UTC 2024");
    }

    public static String getKernelHostname() {
        return propStringDef("kernel.hostname", "localhost");
    }

    public static String getLocaleLanguage() {
        return propStringDef("locale.language", "en");
    }

    public static String getLocaleCountry() {
        return propStringDef("locale.country", "US");
    }

    public static String getInstallerPackage() {
        return propStringDef("install.installer_package", "com.android.vending");
    }

    public static String getInitiatingInstallerPackage() {
        return propStringDef("install.initiating_package", "com.android.vending");
    }

    public static String getGpuVendor() {
        return propStringDef("gpu.vendor", "ARM");
    }

    public static String getGpuRenderer() {
        return propStringDef("gpu.renderer", "Mali-G710 MC10");
    }

    // 0 means "use a default seed".
    public static long getFingerprintSeed() {
        if (!isIdentifierEnabled("fingerprint_seed")) return 0L;
        String configured = getConfigValue("fingerprint.seed");
        if (configured != null && !configured.isEmpty()) {
            try { return Long.parseLong(configured); } catch (NumberFormatException ignored) {}
        }
        String androidId = getAndroidId();
        return RandomGenerator.stableSeed(androidId == null ? "" : androidId);
    }

    public static boolean isIdentifierEnabled(String id) {
        return propBoolDef("enabled." + id, true);
    }

    public static synchronized void setIdentifierEnabled(String id, boolean enabled) {
        if (allProperties == null) init();
        Map<String, String> updated = new HashMap<>(allProperties);
        updated.put("enabled." + id, enabled ? "1" : "0");
        resetCaches();
        allProperties = Collections.unmodifiableMap(updated);
    }

    public static synchronized void setIdentifierValue(String id, String value) {
        if (allProperties == null) init();
        IdentifierRegistry.Definition d = IdentifierRegistry.byId(id);
        if (d == null) return;
        String stored = value == null ? "" : value;
        Map<String, String> updated = new HashMap<>(allProperties);
        for (String key : d.configKeys) {
            updated.put(key, stored);
        }
        resetCaches();
        allProperties = Collections.unmodifiableMap(updated);
    }

    public static synchronized void setProperties(Map<String, String> updates) {
        if (allProperties == null) init();
        if (updates == null || updates.isEmpty()) return;
        Map<String, String> updated = new HashMap<>(allProperties);
        for (Map.Entry<String, String> e : updates.entrySet()) {
            if (e.getKey() == null) continue;
            updated.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        resetCaches();
        allProperties = Collections.unmodifiableMap(updated);
    }

    public static String getRawProperty(String key) {
        String v = getConfigValue(key);
        return v == null ? "" : v;
    }

    public static String getIdentifierValue(String id) {
        Map<String, String> props = allProperties;
        if (props == null) {
            init();
            props = allProperties;
        }
        IdentifierRegistry.Definition d = IdentifierRegistry.byId(id);
        if (d == null) return "";
        String v = props.get(d.primaryKey());
        return v == null ? "" : v;
    }

    public static synchronized void saveConfig(File target) throws IOException {
        if (allProperties == null) init();
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(parent, target.getName() + ".tmp");
        String text = formatConfigText(allProperties);
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(text.getBytes());
            fos.flush();
            try { fos.getFD().sync(); } catch (Exception ignored) {}
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Failed to remove existing " + target);
        }
        if (!tmp.renameTo(target)) {
            throw new IOException("Failed to rename " + tmp + " to " + target);
        }
        target.setReadable(true, false);
    }

    public static synchronized void reload() {
        Map<String, String> loaded = readXSharedPreferences();
        if (loaded == null || loaded.isEmpty()) {
            loaded = readConfigFile();
        }
        Map<String, String> defaults = getEmbeddedDefaults();
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            if (!loaded.containsKey(e.getKey())) {
                loaded.put(e.getKey(), e.getValue());
            }
        }
        resetCaches();
        allProperties = Collections.unmodifiableMap(new HashMap<>(loaded));
    }

    private static String propStringDef(String key, String defaultValue) {
        String v = getConfigValue(key);
        return (v != null && !v.isEmpty()) ? v : defaultValue;
    }

    private static int propIntDef(String key, int defaultValue) {
        String v = getConfigValue(key);
        if (v == null || v.isEmpty()) return defaultValue;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static long propLongDef(String key, long defaultValue) {
        String v = getConfigValue(key);
        if (v == null || v.isEmpty()) return defaultValue;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static boolean propBoolDef(String key, boolean defaultValue) {
        String v = getConfigValue(key);
        if (v == null || v.isEmpty()) return defaultValue;
        return "1".equals(v) || "true".equalsIgnoreCase(v)
                || "yes".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }
}
