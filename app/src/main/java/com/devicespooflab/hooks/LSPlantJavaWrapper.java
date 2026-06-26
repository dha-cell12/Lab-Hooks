package com.devicespooflab.hooks;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LSPlantJavaWrapper {
    private static final String TAG = "DeviceSpoofLab-LSPlant";
    private static final Map<Member, List<ZygiskMethodHook>> hookMap = new HashMap<>();
    private static Method callbackMethod;

    static {
        try {
            callbackMethod = Hooker.class.getDeclaredMethod("callback", Object[].class);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Failed to find Hooker.callback", e);
        }
    }

    public static class Hooker {
        private final Member target;
        public Hooker(Member target) { this.target = target; }

        // LSPlant-compatible callback signature
        public Object callback(Object[] args) throws Throwable {
            return entryPoint(target, this, args);
        }
    }

    public static class Unhook {
        private final Member method;
        private final ZygiskMethodHook callback;

        public Unhook(Member method, ZygiskMethodHook callback) {
            this.method = method;
            this.callback = callback;
        }

        public void unhook() {
            synchronized (hookMap) {
                List<ZygiskMethodHook> hooks = hookMap.get(method);
                if (hooks != null) {
                    hooks.remove(callback);
                    if (hooks.isEmpty()) {
                        hookMap.remove(method);
                        nativeUnhook(method);
                    }
                }
            }
        }
    }

    public static Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0) return null;
        Object last = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(last instanceof ZygiskMethodHook)) return null;

        ZygiskMethodHook callback = (ZygiskMethodHook) last;
        Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = (Class<?>) parameterTypesAndCallback[i];
        }

        try {
            Method m = clazz.getDeclaredMethod(methodName, parameterTypes);
            return hookMethod(m, callback);
        } catch (NoSuchMethodException e) {
            try {
                Method m = clazz.getMethod(methodName, parameterTypes);
                return hookMethod(m, callback);
            } catch (NoSuchMethodException e2) {
                Log.e(TAG, "Method not found: " + methodName + " in " + clazz.getName());
                return null;
            }
        }
    }

    public static Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0) return null;
        Object last = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(last instanceof ZygiskMethodHook)) return null;

        ZygiskMethodHook callback = (ZygiskMethodHook) last;
        Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = (Class<?>) parameterTypesAndCallback[i];
        }

        try {
            java.lang.reflect.Constructor<?> c = clazz.getDeclaredConstructor(parameterTypes);
            return hookMethod(c, callback);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Constructor not found in " + clazz.getName());
            return null;
        }
    }

    public static Unhook hookMethod(Member method, ZygiskMethodHook callback) {
        if (method == null || callback == null) return null;
        synchronized (hookMap) {
            List<ZygiskMethodHook> hooks = hookMap.get(method);
            if (hooks == null) {
                hooks = new ArrayList<>();
                hookMap.put(method, hooks);
                try {
                    Hooker hooker = new Hooker(method);
                    nativeHook(method, hooker, callbackMethod);
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Native hook failed", e);
                }
            }
            hooks.add(callback);
        }
        return new Unhook(method, callback);
    }

    public static Object entryPoint(Member method, Hooker hooker, Object[] args) throws Throwable {
        ZygiskMethodHook.MethodHookParam param = new ZygiskMethodHook.MethodHookParam();
        param.method = method;
        // In LSPlant Object[] args, if it's an instance method, args[0] is the 'this' object
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        if (!isStatic && args != null && args.length > 0) {
            param.thisObject = args[0];
            // Shift args to match Xposed behavior where param.args only contains method arguments
            Object[] shiftedArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, shiftedArgs, 0, args.length - 1);
            param.args = shiftedArgs;
        } else {
            param.thisObject = null;
            param.args = args;
        }

        List<ZygiskMethodHook> hooks;
        synchronized (hookMap) {
            hooks = new ArrayList<>(hookMap.get(method));
        }

        for (ZygiskMethodHook hook : hooks) {
            try {
                hook.beforeHookedMethod(param);
            } catch (Throwable t) {
                Log.e(TAG, "Error in beforeHookedMethod", t);
            }
            if (param.resultSet) return param.getResult();
        }

        Object result = callOriginal(method, param.thisObject, param.args);
        param.setResult(result);
        param.resultSet = false;

        for (ZygiskMethodHook hook : hooks) {
            try {
                hook.afterHookedMethod(param);
            } catch (Throwable t) {
                Log.e(TAG, "Error in afterHookedMethod", t);
            }
        }

        return param.getResult();
    }

    private static native void nativeHook(Member method, Hooker hooker, Method callback);
    private static native void nativeUnhook(Member method);
    private static native Object callOriginal(Member method, Object thisObject, Object[] args) throws Throwable;

    // Reflection Helpers
    public static void setObjectField(Object obj, String name, Object val) {
        try {
            Field f = findFieldInHierarchy(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, val);
        } catch (Exception e) {}
    }
    public static void setStaticObjectField(Class<?> cls, String name, Object val) {
        try {
            Field f = findFieldInHierarchy(cls, name);
            f.setAccessible(true);
            f.set(null, val);
        } catch (Exception e) {}
    }
    public static void setIntField(Object obj, String name, int val) {
        try {
            Field f = findFieldInHierarchy(obj.getClass(), name);
            f.setAccessible(true);
            f.setInt(obj, val);
        } catch (Exception e) {}
    }
    public static void setStaticIntField(Class<?> cls, String name, int val) {
        try {
            Field f = findFieldInHierarchy(cls, name);
            f.setAccessible(true);
            f.setInt(null, val);
        } catch (Exception e) {}
    }
    public static void setBooleanField(Object obj, String name, boolean val) {
        try {
            Field f = findFieldInHierarchy(obj.getClass(), name);
            f.setAccessible(true);
            f.setBoolean(obj, val);
        } catch (Exception e) {}
    }
    public static void setStaticBooleanField(Class<?> cls, String name, boolean val) {
        try {
            Field f = findFieldInHierarchy(cls, name);
            f.setAccessible(true);
            f.setBoolean(null, val);
        } catch (Exception e) {}
    }
    public static void setLongField(Object obj, String name, long val) {
        try {
            Field f = findFieldInHierarchy(obj.getClass(), name);
            f.setAccessible(true);
            f.setLong(obj, val);
        } catch (Exception e) {}
    }
    public static void setStaticLongField(Class<?> cls, String name, long val) {
        try {
            Field f = findFieldInHierarchy(cls, name);
            f.setAccessible(true);
            f.setLong(null, val);
        } catch (Exception e) {}
    }
    public static Object callMethod(Object obj, String name, Object... args) {
        try {
            Class<?>[] types = new Class[args.length];
            for(int i=0; i<args.length; i++) {
                if (args[i] == null) types[i] = Object.class;
                else types[i] = args[i].getClass();
            }
            Method m = findMethodInHierarchy(obj.getClass(), name, types);
            m.setAccessible(true);
            return m.invoke(obj, args);
        } catch (Exception e) { return null; }
    }
    public static Object callStaticMethod(Class<?> cls, String name, Object... args) {
        try {
            Class<?>[] types = new Class[args.length];
            for(int i=0; i<args.length; i++) {
                if (args[i] == null) types[i] = Object.class;
                else types[i] = args[i].getClass();
            }
            Method m = findMethodInHierarchy(cls, name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Exception e) { return null; }
    }

    private static Field findFieldInHierarchy(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> curr = cls;
        while (curr != null) {
            try {
                return curr.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                curr = curr.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethodInHierarchy(Class<?> cls, String name, Class<?>[] types) throws NoSuchMethodException {
        Class<?> curr = cls;
        while (curr != null) {
            try {
                return curr.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException e) {
                curr = curr.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    public static Object getObjectField(Object obj, String name) {
        try {
            Field f = findFieldInHierarchy(obj.getClass(), name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) { return null; }
    }

    public static Object getStaticObjectField(Class<?> cls, String name) {
        try {
            Field f = findFieldInHierarchy(cls, name);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) { return null; }
    }
}
