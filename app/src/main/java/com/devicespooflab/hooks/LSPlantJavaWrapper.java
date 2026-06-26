package com.devicespooflab.hooks;

import android.util.Log;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LSPlantJavaWrapper {
    private static final String TAG = "DeviceSpoofLab-LSPlant";
    private static final Map<Member, List<ZygiskMethodHook>> hookMap = new HashMap<>();

    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0) return;
        Object last = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(last instanceof ZygiskMethodHook)) return;

        ZygiskMethodHook callback = (ZygiskMethodHook) last;
        Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = (Class<?>) parameterTypesAndCallback[i];
        }

        try {
            Method m = clazz.getDeclaredMethod(methodName, parameterTypes);
            hookMethod(m, callback);
        } catch (NoSuchMethodException e) {
            try {
                // Try to find in the whole hierarchy if getDeclaredMethod fails
                Method m = clazz.getMethod(methodName, parameterTypes);
                hookMethod(m, callback);
            } catch (NoSuchMethodException e2) {
                Log.e(TAG, "Method not found: " + methodName + " in " + clazz.getName(), e);
            }
        }
    }

    public static void findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0) return;
        Object last = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(last instanceof ZygiskMethodHook)) return;

        ZygiskMethodHook callback = (ZygiskMethodHook) last;
        Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = (Class<?>) parameterTypesAndCallback[i];
        }

        try {
            java.lang.reflect.Constructor<?> c = clazz.getDeclaredConstructor(parameterTypes);
            hookMethod(c, callback);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Constructor not found in " + clazz.getName(), e);
        }
    }

    public static void hookMethod(Member method, ZygiskMethodHook callback) {
        if (method == null || callback == null) return;
        synchronized (hookMap) {
            List<ZygiskMethodHook> hooks = hookMap.get(method);
            if (hooks == null) {
                hooks = new ArrayList<>();
                hookMap.put(method, hooks);
                try {
                    nativeHook(method);
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Native hook failed", e);
                }
            }
            hooks.add(callback);
        }
    }

    // Called from Native
    public static Object entryPoint(Member method, Object thisObject, Object[] args) throws Throwable {
        ZygiskMethodHook.MethodHookParam param = new ZygiskMethodHook.MethodHookParam();
        param.method = method;
        param.thisObject = thisObject;
        param.args = args;

        List<ZygiskMethodHook> hooks;
        synchronized (hookMap) {
            hooks = new ArrayList<>(hookMap.get(method));
        }

        // Before hooks
        for (ZygiskMethodHook hook : hooks) {
            try {
                hook.beforeHookedMethod(param);
            } catch (Throwable t) {
                Log.e(TAG, "Error in beforeHookedMethod", t);
            }
            if (param.resultSet) return param.getResult();
        }

        // Call original
        Object result = callOriginal(method, thisObject, args);
        param.setResult(result);
        param.resultSet = false; // Reset for after hooks

        // After hooks
        for (ZygiskMethodHook hook : hooks) {
            try {
                hook.afterHookedMethod(param);
            } catch (Throwable t) {
                Log.e(TAG, "Error in afterHookedMethod", t);
            }
        }

        return param.getResult();
    }

    private static native void nativeHook(Member method);
    private static native Object callOriginal(Member method, Object thisObject, Object[] args) throws Throwable;
}
