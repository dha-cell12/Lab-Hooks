package com.devicespooflab.hooks.hooks;

import android.os.Build;
import android.os.IInterface;

import com.devicespooflab.hooks.utils.ConfigManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

public class AppSetIdHooks {

    private static final String TAG = "DeviceSpoofLab-AppSetId";
    private static final int MIN_SDK = 30;

    private static final String IAPPSET_SERVICE_DESCRIPTOR =
            "com.google.android.gms.appset.internal.IAppSetService";

    public static void hook(LoadPackageParam lpparam) {
        hook(lpparam, Build.VERSION.SDK_INT);
    }

    public static void hook(LoadPackageParam lpparam, int realDeviceSdk) {
        if (realDeviceSdk < MIN_SDK) {
            return;
        }

        try {
            hookClientSide(lpparam);
        } catch (Exception e) {
            XposedBridge.log(TAG + ": client hook failed: " + e.getMessage());
        }

        if ("com.google.android.gms".equals(lpparam.packageName)) {
            try {
                hookGmsServerSide();
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": GMS hook failed: " + t.getMessage());
            }
        }
    }

    // ---- Client-side substitution ----

    private static void hookClientSide(LoadPackageParam lpparam) {
        Class<?> appSetIdInfoClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.appset.AppSetIdInfo", lpparam.classLoader);
        if (appSetIdInfoClass != null) {
            try {
                XposedHelpers.findAndHookMethod(appSetIdInfoClass, "getId",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getAppSetId();
                                if (v != null) param.setResult(v);
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
            try {
                // Scope: 1 = APP (per-app id), 2 = DEVELOPER (shared).
                XposedHelpers.findAndHookMethod(appSetIdInfoClass, "getScope",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                param.setResult(1);
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
            // Rewrite constructor args so reflective field reads also see
            // the spoofed value.
            try {
                XposedHelpers.findAndHookConstructor(appSetIdInfoClass,
                        String.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getAppSetId();
                                if (v != null) param.args[0] = v;
                                param.args[1] = 1;
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
        }

        // AIDL service proxy — for callers that bypass AppSetIdInfo entirely.
        Class<?> appSetServiceStub = XposedHelpers.findClassIfExists(
                "com.google.android.gms.appset.internal.IAppSetService$Stub$Proxy",
                lpparam.classLoader);
        if (appSetServiceStub != null) {
            try {
                XposedHelpers.findAndHookMethod(appSetServiceStub, "getAppSetIdInfo",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object info = param.getResult();
                                if (info == null) return;
                                String v = ConfigManager.getAppSetId();
                                if (v == null) return;
                                try {
                                    XposedHelpers.setObjectField(info, "id", v);
                                    XposedHelpers.setIntField(info, "scope", 1);
                                } catch (Throwable ignored) {
                                }
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
        }

        // Android 14+ Privacy Sandbox AppSetId. The framework constructs this
        // from the IPC reply, so the constructor hook catches the value as it
        // crosses into app code.
        Class<?> systemAppSetId = XposedHelpers.findClassIfExists(
                "android.adservices.appsetid.AppSetId", lpparam.classLoader);
        if (systemAppSetId != null) {
            try {
                XposedHelpers.findAndHookMethod(systemAppSetId, "getId",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getAppSetId();
                                if (v != null) param.setResult(v);
                            }
                        });
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(systemAppSetId, "getScope",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                param.setResult(1);
                            }
                        });
            } catch (Throwable ignored) {
            }
            for (Constructor<?> c : systemAppSetId.getDeclaredConstructors()) {
                Class<?>[] types = c.getParameterTypes();
                if (types.length >= 1 && types[0] == String.class) {
                    try {
                        XposedBridge.hookMethod(c, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getAppSetId();
                                if (v != null) param.args[0] = v;
                                if (param.args.length > 1
                                        && param.args[1] instanceof Integer) {
                                    param.args[1] = 1;
                                }
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    // ---- GMS-side: rewrite the payload before it leaves the server ----

    private static final AtomicBoolean sAttachWatcherInstalled = new AtomicBoolean(false);
    private static final Set<String> sApiBinderHookedClasses =
            Collections.synchronizedSet(new HashSet<String>());

    private static void hookGmsServerSide() {
        if (!sAttachWatcherInstalled.compareAndSet(false, true)) return;
        // Every AIDL Stub calls Binder.attachInterface(this, DESCRIPTOR) in
        // its constructor. The descriptor string survives R8, so this gives
        // us a stable hook on the (renamed) AppSet Stub class without having
        // to chase chimera plumbing.
        try {
            XposedHelpers.findAndHookMethod(android.os.Binder.class, "attachInterface",
                    IInterface.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!IAPPSET_SERVICE_DESCRIPTOR.equals(param.args[1])) return;
                            Object stub = param.args[0];
                            if (stub != null) {
                                installAppSetStubHooks(stub.getClass());
                            }
                        }
                    });
        } catch (Throwable t) {
            sAttachWatcherInstalled.set(false);
            XposedBridge.log(TAG + ": attachInterface watcher failed: " + t.getMessage());
        }
    }

    private static void installAppSetStubHooks(Class<?> stubClass) {
        if (!sApiBinderHookedClasses.add(stubClass.getName())) return;
        // Hook every non-framework declared method on the stub. The AppSet
        // AIDL method is (AppSetIdRequestParams, IAppSetIdCallback) under R8
        // renaming; wrap any IInterface arg so the server's callback
        // delivery passes through our payload rewrite.
        for (Method m : stubClass.getDeclaredMethods()) {
            if (Modifier.isAbstract(m.getModifiers())) continue;
            if (Modifier.isStatic(m.getModifiers())) continue;
            String n = m.getName();
            if ("asBinder".equals(n) || "onTransact".equals(n)
                    || "getInterfaceDescriptor".equals(n)
                    || "queryLocalInterface".equals(n)
                    || "dispatchTransaction".equals(n)
                    || "toString".equals(n)) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args == null) return;
                        for (int i = 0; i < param.args.length; i++) {
                            Object a = param.args[i];
                            if (a instanceof IInterface) {
                                Object wrapped = wrapCallback(a);
                                if (wrapped != null) param.args[i] = wrapped;
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": hook " + stubClass.getSimpleName()
                        + "." + n + " failed: " + t.getMessage());
            }
        }
    }

    // Wrap the callback so every invocation passes through us. When the
    // server calls back with an AppSetInfoParcel-shaped object, rewrite its
    // String field before the call serialises into binder.
    private static Object wrapCallback(Object cb) {
        Class<?>[] ifaces = collectInterfaces(cb.getClass());
        if (ifaces.length == 0) return null;
        final Object delegate = cb;
        try {
            return Proxy.newProxyInstance(cb.getClass().getClassLoader(), ifaces,
                    (proxy, method, args) -> {
                        if (args != null) {
                            for (Object arg : args) {
                                if (arg == null) continue;
                                maybeRewriteAppSetPayload(arg);
                            }
                        }
                        return method.invoke(delegate, args);
                    });
        } catch (Throwable t) {
            return null;
        }
    }

    private static Class<?>[] collectInterfaces(Class<?> cls) {
        LinkedHashSet<Class<?>> set = new LinkedHashSet<>();
        Class<?> walk = cls;
        while (walk != null && walk != Object.class) {
            for (Class<?> i : walk.getInterfaces()) set.add(i);
            walk = walk.getSuperclass();
        }
        return set.toArray(new Class<?>[0]);
    }

    // The App Set ID is a UUID (RFC 4122). Match the id slot by value, not by
    // field position, so an unrelated leading String field never receives it.
    private static final Pattern APP_SET_ID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    // The AppSet callback payload (com.google.android.gms.appset.AppSetInfoParcel
    // in current GMS) keeps its canonical class name because it is a Parcelable,
    // but R8 renames its fields, so we cannot match the id by name. Locate it by
    // its UUID value instead — robust to field ordering and to any extra String
    // field (package name, class id) the parcel may carry — and force scope =
    // APP (1) to match the client-side hooks when the scope int is unambiguous.
    private static void maybeRewriteAppSetPayload(Object obj) {
        Class<?> c = obj.getClass();
        String n = c.getName();
        if (!n.contains("AppSet") && !n.contains("appset")) return;
        String spoof = ConfigManager.getAppSetId();
        if (spoof == null) return;

        Field idField = null;
        Field soleStringField = null;
        int stringFieldCount = 0;
        Field soleIntField = null;
        int intFieldCount = 0;

        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Class<?> t = f.getType();
            if (t == String.class) {
                stringFieldCount++;
                soleStringField = f;
                if (idField == null) {
                    try {
                        f.setAccessible(true);
                        Object cur = f.get(obj);
                        if (cur instanceof String
                                && APP_SET_ID_PATTERN.matcher((String) cur).matches()) {
                            idField = f;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } else if (t == int.class) {
                intFieldCount++;
                soleIntField = f;
            }
        }

        // Prefer the UUID-shaped field; fall back to the only String field when
        // the payload has exactly one (still unambiguous). Never guess among
        // several non-UUID strings — that is how the old positional rewrite put
        // the id into the wrong slot and silently failed.
        Field target = idField != null ? idField
                : (stringFieldCount == 1 ? soleStringField : null);
        if (target == null) {
            XposedBridge.log(TAG + ": " + n + " exposed no UUID-shaped id ("
                    + stringFieldCount + " string fields); skipping rewrite");
            return;
        }
        try {
            target.setAccessible(true);
            target.set(obj, spoof);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": id rewrite on " + n + " failed: " + t.getMessage());
            return;
        }

        // Only force scope once we are confident this is AppSetInfoParcel (UUID
        // id matched) and the scope int is unambiguous. A second int field is
        // most likely a SafeParcelable versionCode; overwriting it would corrupt
        // parcel readback, so leave scope untouched in that case.
        if (idField != null && intFieldCount == 1) {
            try {
                soleIntField.setAccessible(true);
                soleIntField.setInt(obj, 1);
            } catch (Throwable ignored) {
            }
        }
    }
}
