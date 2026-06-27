package com.devicespooflab.hooks.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

// Minimal Xposed-compatible helper API backed by XposedBridge/LSPlant. Only the
// overloads actually used by the hooks/ classes are implemented; the resolver
// convention mirrors classic Xposed: the trailing element of
// parameterTypesAndCallback is the XC_MethodHook, the preceding elements are
// parameter types (either a Class<?> or a class-name String).
public final class XposedHelpers {

    private XposedHelpers() {}

    // ---- Class lookup -----------------------------------------------------

    public static Class<?> findClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false,
                    loader != null ? loader : XposedHelpers.class.getClassLoader());
        } catch (Throwable t) {
            throw new ClassNotFoundError(className, t);
        }
    }

    public static Class<?> findClassIfExists(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false,
                    loader != null ? loader : XposedHelpers.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- Method / constructor hooking ------------------------------------

    public static XposedBridge.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = extractCallback(parameterTypesAndCallback);
        Class<?>[] paramTypes = resolveParamTypes(
                clazz.getClassLoader(), parameterTypesAndCallback);
        Method m = findMethodExact(clazz, methodName, paramTypes);
        return XposedBridge.hookMethod(m, callback);
    }

    public static XposedBridge.Unhook findAndHookMethod(
            String className, ClassLoader loader, String methodName,
            Object... parameterTypesAndCallback) {
        return findAndHookMethod(
                findClass(className, loader), methodName, parameterTypesAndCallback);
    }

    public static XposedBridge.Unhook findAndHookConstructor(
            Class<?> clazz, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = extractCallback(parameterTypesAndCallback);
        Class<?>[] paramTypes = resolveParamTypes(
                clazz.getClassLoader(), parameterTypesAndCallback);
        Constructor<?> c = findConstructorExact(clazz, paramTypes);
        return XposedBridge.hookMethod(c, callback);
    }

    public static XposedBridge.Unhook findAndHookConstructor(
            String className, ClassLoader loader, Object... parameterTypesAndCallback) {
        return findAndHookConstructor(
                findClass(className, loader), parameterTypesAndCallback);
    }

    // ---- Reflective invocation -------------------------------------------

    public static Object callMethod(Object obj, String methodName, Object... args) {
        if (obj == null) throw new IllegalArgumentException("callMethod: null obj");
        Method m = findMethodByArgs(obj.getClass(), methodName, args);
        try {
            m.setAccessible(true);
            return m.invoke(obj, args);
        } catch (Throwable t) {
            throw unwrap("callMethod " + methodName, t);
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        Method m = findMethodByArgs(clazz, methodName, args);
        try {
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable t) {
            throw unwrap("callStaticMethod " + methodName, t);
        }
    }

    // ---- Instance field accessors ----------------------------------------

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            field(obj.getClass(), fieldName).set(obj, value);
        } catch (Throwable t) {
            throw unwrap("setObjectField " + fieldName, t);
        }
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        try {
            field(obj.getClass(), fieldName).setInt(obj, value);
        } catch (Throwable t) {
            throw unwrap("setIntField " + fieldName, t);
        }
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        try {
            field(obj.getClass(), fieldName).setBoolean(obj, value);
        } catch (Throwable t) {
            throw unwrap("setBooleanField " + fieldName, t);
        }
    }

    public static void setLongField(Object obj, String fieldName, long value) {
        try {
            field(obj.getClass(), fieldName).setLong(obj, value);
        } catch (Throwable t) {
            throw unwrap("setLongField " + fieldName, t);
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return field(obj.getClass(), fieldName).get(obj);
        } catch (Throwable t) {
            throw unwrap("getObjectField " + fieldName, t);
        }
    }

    // ---- Static field accessors ------------------------------------------

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            field(clazz, fieldName).set(null, value);
        } catch (Throwable t) {
            throw unwrap("setStaticObjectField " + fieldName, t);
        }
    }

    public static void setStaticIntField(Class<?> clazz, String fieldName, int value) {
        try {
            field(clazz, fieldName).setInt(null, value);
        } catch (Throwable t) {
            throw unwrap("setStaticIntField " + fieldName, t);
        }
    }

    public static void setStaticBooleanField(Class<?> clazz, String fieldName, boolean value) {
        try {
            field(clazz, fieldName).setBoolean(null, value);
        } catch (Throwable t) {
            throw unwrap("setStaticBooleanField " + fieldName, t);
        }
    }

    public static void setStaticLongField(Class<?> clazz, String fieldName, long value) {
        try {
            field(clazz, fieldName).setLong(null, value);
        } catch (Throwable t) {
            throw unwrap("setStaticLongField " + fieldName, t);
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            return field(clazz, fieldName).get(null);
        } catch (Throwable t) {
            throw unwrap("getStaticObjectField " + fieldName, t);
        }
    }

    // ---- Internal resolver helpers ---------------------------------------

    private static XC_MethodHook extractCallback(Object[] arr) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("missing XC_MethodHook callback");
        }
        Object last = arr[arr.length - 1];
        if (!(last instanceof XC_MethodHook)) {
            throw new IllegalArgumentException(
                    "last argument must be XC_MethodHook, got " + last);
        }
        return (XC_MethodHook) last;
    }

    private static Class<?>[] resolveParamTypes(ClassLoader loader, Object[] arr) {
        int n = arr.length - 1; // exclude trailing callback
        Class<?>[] types = new Class<?>[n];
        for (int i = 0; i < n; i++) {
            Object spec = arr[i];
            if (spec instanceof Class<?>) {
                types[i] = (Class<?>) spec;
            } else if (spec instanceof String) {
                types[i] = findClass((String) spec, loader);
            } else {
                throw new IllegalArgumentException(
                        "parameter type must be Class or String, got " + spec);
            }
        }
        return types;
    }

    private static Method findMethodExact(Class<?> clazz, String name, Class<?>[] paramTypes) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "." + name + describe(paramTypes));
    }

    private static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>[] paramTypes) {
        try {
            Constructor<?> c = clazz.getDeclaredConstructor(paramTypes);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(
                    clazz.getName() + ".<init>" + describe(paramTypes));
        }
    }

    private static Method findMethodByArgs(Class<?> clazz, String name, Object[] args) {
        Class<?> c = clazz;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (paramsAssignable(m.getParameterTypes(), args)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        throw new NoSuchMethodError(clazz.getName() + "." + name + " (by args)");
    }

    private static boolean paramsAssignable(Class<?>[] params, Object[] args) {
        int alen = args != null ? args.length : 0;
        if (params.length != alen) return false;
        for (int i = 0; i < params.length; i++) {
            Object a = args[i];
            if (a == null) {
                if (params[i].isPrimitive()) return false;
            } else if (!wrap(params[i]).isAssignableFrom(a.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Field field(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(clazz.getName() + "." + name);
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        if (c == short.class) return Short.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        return c;
    }

    private static String describe(Class<?>[] paramTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes[i].getName());
        }
        return sb.append(")").toString();
    }

    private static RuntimeException unwrap(String what, Throwable t) {
        Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException
                && t.getCause() != null) ? t.getCause() : t;
        return new RuntimeException(what + ": " + cause.getMessage(), cause);
    }

    public static final class ClassNotFoundError extends Error {
        public ClassNotFoundError(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
