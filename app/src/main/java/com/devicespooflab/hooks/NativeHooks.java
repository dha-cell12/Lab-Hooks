package com.devicespooflab.hooks;

import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.devicespooflab.hooks.utils.ConfigManager;

public final class NativeHooks {

    private static final String TAG = "DeviceSpoofLab-Native";
    private static final String LIB_NAME = "ds_native";

    private static volatile boolean libraryLoaded = false;
    private static volatile boolean installed = false;

    private NativeHooks() {}

    private static native int nativeInstall(HashMap<String, String> props);

    private static native String nativeQuery(String key);

    private static native java.util.HashMap<String, String> nativeGetAll();

    public static synchronized boolean tryInstall(Map<String, String> props) {
        if (installed) {
            return true;
        }
        if (!loadLibrary()) {
            return false;
        }
        try {
            HashMap<String, String> hashMap = (props instanceof HashMap)
                    ? (HashMap<String, String>) props
                    : new HashMap<>(props);
            int n = nativeInstall(hashMap);
            installed = (n >= 0);
            if (ConfigManager.isVerboseLoggingEnabled()) {
                Log.i(TAG, "nativeInstall returned " + n);
            }
            return installed;
        } catch (Throwable t) {
            Log.w(TAG, "nativeInstall threw", t);
            return false;
        }
    }

    public static String debugQuery(String key) {
        if (!installed) return null;
        try {
            return nativeQuery(key);
        } catch (Throwable t) {
            return null;
        }
    }

    // Returns the full companion-sourced profile from the native layer, or an
    // empty map if the library isn't loaded or the call fails. Lets the Java
    // config layer treat native g_props as the single source of truth.
    public static java.util.HashMap<String, String> getAllFromNative() {
        if (!loadLibrary()) {
            return new java.util.HashMap<>();
        }
        try {
            java.util.HashMap<String, String> all = nativeGetAll();
            return all != null ? all : new java.util.HashMap<>();
        } catch (Throwable t) {
            return new java.util.HashMap<>();
        }
    }

    private static boolean loadLibrary() {
        if (libraryLoaded) return true;

        try {
            System.loadLibrary(LIB_NAME);
            libraryLoaded = true;
            return true;
        } catch (UnsatisfiedLinkError ignored) {
        }

        // Fallback: look the .so up via the module classloader, then a small
        // set of known absolute paths. Runs during app startup; no filesystem walk.
        String absPath = locateLibraryOnDisk();
        if (absPath == null) {
            Log.w(TAG, "could not locate " + LIB_NAME + " .so");
            return false;
        }
        try {
            System.load(absPath);
            libraryLoaded = true;
            if (ConfigManager.isVerboseLoggingEnabled()) {
                Log.i(TAG, "loaded native lib via System.load(" + absPath + ")");
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "System.load(" + absPath + ") failed", t);
            return false;
        }
    }

    private static String locateLibraryOnDisk() {
        String modulePkg = "com.devicespooflab.hooks";
        String soName = System.mapLibraryName(LIB_NAME);

        String classLoaderPath = findLibraryFromClassLoader();
        if (isUsableLibraryPath(classLoaderPath, soName)) {
            return classLoaderPath;
        }

        String[] exactPaths = new String[] {
                "/data/data/" + modulePkg + "/lib/" + soName,
                "/data/user/0/" + modulePkg + "/lib/" + soName,
        };
        for (String path : exactPaths) {
            if (path.contains(modulePkg) && isUsableLibraryPath(path, soName)) {
                return path;
            }
        }

        return null;
    }

    private static String findLibraryFromClassLoader() {
        ClassLoader loader = NativeHooks.class.getClassLoader();
        if (loader == null) {
            return null;
        }
        Class<?> clazz = loader.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod("findLibrary", String.class);
                method.setAccessible(true);
                Object value = method.invoke(loader, LIB_NAME);
                if (value instanceof String && !((String) value).isEmpty()) {
                    return (String) value;
                }
                return null;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isUsableLibraryPath(String path, String soName) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (!path.endsWith("/" + soName)) {
            return false;
        }
        try {
            File file = new File(path);
            return file.isFile() && file.canRead();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
