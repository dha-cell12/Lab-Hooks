package com.devicespooflab.hooks.hooks;

import com.devicespooflab.hooks.utils.ConfigManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.devicespooflab.hooks.xposed.XC_MethodHook;
import com.devicespooflab.hooks.xposed.XposedBridge;
import com.devicespooflab.hooks.xposed.XposedHelpers;
import com.devicespooflab.hooks.xposed.LoadPackageParam;

// GAID spoof. Client-side AdvertisingIdClient hook + GMS-side
// AdvertisingIdChimeraService.onBind hook + IAdvertisingIdService$Stub.onTransact
// parcel rewrite as a fallback when R8 renames the service.
public class AdvertisingIdHooks {

    private static final String GAID_STUB_NAME =
            "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService$Stub";
    // The AIDL interface descriptor survives R8 obfuscation (it's the binder
    // token), unlike the Stub/Chimera class names. Every Stub ctor calls
    // Binder.attachInterface(this, DESCRIPTOR), so this is our obfuscation-proof
    // discovery key inside GMS.
    private static final String GAID_AIDL_DESCRIPTOR =
            "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService";
    private static final String CHIMERA_SERVICE_NAME_A =
            "com.google.android.gms.adid.service.AdvertisingIdChimeraService";
    private static final String CHIMERA_SERVICE_NAME_B =
            "com.google.android.gms.ads.identifier.service.AdvertisingIdChimeraService";

    private static final AtomicBoolean sStubHookInstalled = new AtomicBoolean(false);
    private static final AtomicBoolean sChimeraOnBindInstalled = new AtomicBoolean(false);
    private static final AtomicBoolean sGetIdHooked = new AtomicBoolean(false);
    private static final AtomicBoolean sAttachIfaceHooked = new AtomicBoolean(false);

    private static final List<XposedBridge.Unhook> sWatcherUnhooks = new ArrayList<>();
    private static final AtomicBoolean sWatcherRetired = new AtomicBoolean(false);
    private static volatile long sWatcherDeadlineNanos = 0L;
    private static final long WATCHER_BUDGET_NANOS = 120_000_000_000L;

    public static void hook(LoadPackageParam lpparam) {
        Class<?> advertisingIdInfoClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient$Info",
                lpparam.classLoader);
        if (advertisingIdInfoClass != null) {
            try {
                XposedHelpers.findAndHookMethod(advertisingIdInfoClass, "getId",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getGAID();
                                if (v != null) param.setResult(v);
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
            // Some callers read the adId field via reflection or through the
            // cached Info instance without invoking getId(). Rewriting the
            // constructor argument means any accessor sees the spoofed value.
            try {
                XposedHelpers.findAndHookConstructor(advertisingIdInfoClass,
                        String.class, boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getGAID();
                                if (v != null) param.args[0] = v;
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
        }

        // AIDL stub proxy — covers callers that bypass the Info class and
        // invoke IAdvertisingIdService directly via reflection.
        Class<?> adIdServiceStub = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService$Stub$Proxy",
                lpparam.classLoader);
        if (adIdServiceStub != null) {
            try {
                XposedHelpers.findAndHookMethod(adIdServiceStub, "getId",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getGAID();
                                if (v != null) param.setResult(v);
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
        }

        // Android 14+ Privacy Sandbox AdId. The framework constructs this
        // from the IPC reply, so the constructor hook catches the value as
        // it crosses into app code.
        Class<?> adIdClass = XposedHelpers.findClassIfExists(
                "android.adservices.adid.AdId", lpparam.classLoader);
        if (adIdClass != null) {
            try {
                XposedHelpers.findAndHookMethod(adIdClass, "getAdId",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getGAID();
                                if (v != null) param.setResult(v);
                            }
                        });
            } catch (NoSuchMethodError ignored) {
            }
            for (java.lang.reflect.Constructor<?> c : adIdClass.getDeclaredConstructors()) {
                Class<?>[] types = c.getParameterTypes();
                if (types.length >= 1 && types[0] == String.class) {
                    try {
                        XposedBridge.hookMethod(c, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String v = ConfigManager.getGAID();
                                if (v != null) param.args[0] = v;
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        // GMS-side: hook the actual server impl so it never produces the
        // real id in the first place. attachInterface discovery is the reliable
        // path (descriptor survives obfuscation); the name-based watcher stays
        // as a fallback for builds where the descriptor differs.
        if ("com.google.android.gms".equals(lpparam.packageName)) {
            installGmsAdIdAttachInterfaceHook();
            hookIAdvertisingIdServiceStub(lpparam);
        }
    }

    // Catches the adid AIDL stub by its interface descriptor at bind time,
    // independent of how R8 renamed the Stub / Chimera service classes. Runs in
    // the GMS process; rewrites the getId() reply for every client (so even an
    // obfuscated/shaded AdvertisingIdClient in the target app gets the spoof).
    private static void installGmsAdIdAttachInterfaceHook() {
        if (!sAttachIfaceHooked.compareAndSet(false, true)) return;
        try {
            XposedHelpers.findAndHookMethod(android.os.Binder.class, "attachInterface",
                    android.os.IInterface.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (sStubHookInstalled.get()) return;
                                if (!GAID_AIDL_DESCRIPTOR.equals(param.args[1])) return;
                                Object stub = param.thisObject;
                                if (stub == null) return;
                                Class<?> dispatcher = findOnTransactDeclaringClass(stub.getClass());
                                if (dispatcher != null) {
                                    installStubOnTransactHook(dispatcher);
                                }
                            } catch (Throwable ignored) {
                                // Discovery must never disturb the binder setup.
                            }
                        }
                    });
        } catch (Throwable t) {
            sAttachIfaceHooked.set(false);
            XposedBridge.log("DeviceSpoofLab-GAID: attachInterface discovery install failed: "
                    + t.getMessage());
        }
    }

    private static void hookIAdvertisingIdServiceStub(LoadPackageParam lpparam) {
        // Direct path: if the Stub class is already on the classpath, hook it now
        // and skip the class-load watcher entirely.
        Class<?> stub = XposedHelpers.findClassIfExists(GAID_STUB_NAME, lpparam.classLoader);
        if (stub != null) {
            installStubOnTransactHook(stub);
            return;
        }
        // Deferred path: the stub / Chimera impl is loaded lazily under a name we
        // can't reference statically. Rather than hooking ClassLoader.loadClass on
        // the base class — which fires for *every* lookup in *every* loader in the
        // process (cache hits and parent-delegation passes included) and risks
        // reentrancy if the callback ever triggers a class load — watch the single
        // narrow choke point that every dex-backed loader funnels real class
        // *definitions* through: BaseDexClassLoader.findClass(String). Standard
        // loaders (PathClassLoader / DexClassLoader / DelegateLastClassLoader) and
        // the Chimera module loaders all inherit it, it fires only on first
        // definition, and the watcher retires itself the instant it lands a
        // getId() hook (see watchForStub / removeWatchers).
        XC_MethodHook watcher = watchForStub();
        sWatcherDeadlineNanos = System.nanoTime() + WATCHER_BUDGET_NANOS;
        try {
            Class<?> baseDex = Class.forName("dalvik.system.BaseDexClassLoader");
            XposedBridge.Unhook u = XposedHelpers.findAndHookMethod(
                    baseDex, "findClass", String.class, watcher);
            synchronized (sWatcherUnhooks) {
                sWatcherUnhooks.add(u);
            }
        } catch (Throwable t) {
            XposedBridge.log("DeviceSpoofLab-GAID: class-load watcher install failed: "
                    + t.getMessage());
        }
    }

    private static XC_MethodHook watchForStub() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (sWatcherRetired.get()) return;
                // Retire on a time budget so a renamed (never-matching) service
                // can't keep us intercepting class loads for the whole process.
                // Overflow-safe nanoTime comparison.
                if (System.nanoTime() - sWatcherDeadlineNanos > 0) {
                    removeWatchers();
                    return;
                }
                try {
                    Object result = param.getResult();
                    if (!(result instanceof Class)) return;
                    Class<?> cls = (Class<?>) result;
                    // Hot path is String-only (getName + equals): it touches no
                    // uninitialised classes, so it never re-enters findClass().
                    String n = cls.getName();
                    if (GAID_STUB_NAME.equals(n)) {
                        installStubOnTransactHook(cls);
                        if (sStubHookInstalled.get()) removeWatchers();
                        return;
                    }
                    // The actual AIDL impl is AdvertisingIdChimeraService. We
                    // can't hook getId() statically (it's loaded later via
                    // Chimera and named differently), so hook onBind() to grab
                    // the binder instance at runtime.
                    if (CHIMERA_SERVICE_NAME_A.equals(n) || CHIMERA_SERVICE_NAME_B.equals(n)) {
                        installChimeraServiceOnBindHook(cls);
                        if (sChimeraOnBindInstalled.get()) removeWatchers();
                    }
                } catch (Throwable ignored) {
                    // Watcher bookkeeping must never disturb the class load.
                }
            }
        };
    }

    private static void removeWatchers() {
        if (!sWatcherRetired.compareAndSet(false, true)) return;
        synchronized (sWatcherUnhooks) {
            for (XposedBridge.Unhook u : sWatcherUnhooks) {
                try { u.unhook(); } catch (Throwable ignored) {}
            }
            sWatcherUnhooks.clear();
        }
    }

    private static void installChimeraServiceOnBindHook(Class<?> cls) {
        if (!sChimeraOnBindInstalled.compareAndSet(false, true)) return;
        try {
            XposedHelpers.findAndHookMethod(cls, "onBind",
                    android.content.Intent.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object binder = param.getResult();
                            if (binder == null) return;
                            Class<?> bc = binder.getClass();
                            Method m = findGetIdInHierarchy(bc);
                            if (m != null) {
                                hookGetIdMethod(m);
                                return;
                            }
                            // R8 may rename getId(); rewrite the reply on the
                            // AIDL onTransact dispatcher instead.
                            Class<?> dispatcher = findOnTransactDeclaringClass(bc);
                            if (dispatcher != null) {
                                installStubOnTransactHook(dispatcher);
                            }
                        }
                    });
        } catch (Throwable t) {
            sChimeraOnBindInstalled.set(false);
            XposedBridge.log("DeviceSpoofLab-GAID: ChimeraService.onBind hook failed: "
                    + t.getMessage());
        }
    }

    private static Class<?> findOnTransactDeclaringClass(Class<?> cls) {
        Class<?> walk = cls;
        while (walk != null && walk != Object.class && walk != android.os.Binder.class) {
            try {
                walk.getDeclaredMethod("onTransact", int.class,
                        android.os.Parcel.class, android.os.Parcel.class, int.class);
                return walk;
            } catch (NoSuchMethodException ignored) {
            }
            walk = walk.getSuperclass();
        }
        return null;
    }

    private static Method findGetIdInHierarchy(Class<?> cls) {
        Class<?> walk = cls;
        while (walk != null && walk != Object.class) {
            for (Method m : walk.getDeclaredMethods()) {
                if ("getId".equals(m.getName())
                        && m.getReturnType() == String.class
                        && m.getParameterTypes().length == 0
                        && !Modifier.isAbstract(m.getModifiers())) {
                    return m;
                }
            }
            walk = walk.getSuperclass();
        }
        return null;
    }

    private static void hookGetIdMethod(Method m) {
        if (!sGetIdHooked.compareAndSet(false, true)) return;
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String v = ConfigManager.getGAID();
                    if (v != null) param.setResult(v);
                }
            });
        } catch (Throwable t) {
            sGetIdHooked.set(false);
        }
    }

    private static void installStubOnTransactHook(Class<?> stub) {
        if (!sStubHookInstalled.compareAndSet(false, true)) return;
        try {
            XposedHelpers.findAndHookMethod(stub, "onTransact",
                    int.class, android.os.Parcel.class, android.os.Parcel.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int code = (int) param.args[0];
                            android.os.Parcel reply = (android.os.Parcel) param.args[2];
                            if (reply == null) return;
                            // AIDL: getId() = TX code 1.
                            if (code != 1) return;
                            String spoof = ConfigManager.getGAID();
                            if (spoof == null) return;
                            try {
                                reply.setDataSize(0);
                                reply.setDataPosition(0);
                                reply.writeNoException();
                                reply.writeString(spoof);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            sStubHookInstalled.set(false);
        }
    }
}
