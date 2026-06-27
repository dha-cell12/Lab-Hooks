package com.devicespooflab.hooks.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// Minimal Xposed-compatible bridge backed by LSPlant. The hooks/ classes call
// the static API here (and via XposedHelpers); installation is delegated to
// the native nativeHookMethod() which calls lsplant::Hook.
public final class XposedBridge {

    private static final String TAG = "DS-Xposed";

    private XposedBridge() {}

    // Maps an already-hooked Member to its LspHooker so that a second hook on
    // the same Member adds a callback instead of installing a duplicate hook.
    private static final Map<Member, LspHooker> HOOKERS =
            Collections.synchronizedMap(new WeakHashMap<Member, LspHooker>());

    // The LSPlant callback Method (LspHooker.callback(Object[])), resolved once.
    private static final Method CALLBACK_METHOD;
    static {
        Method m;
        try {
            m = LspHooker.class.getDeclaredMethod("callback", Object[].class);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
        CALLBACK_METHOD = m;
    }

    // Installs (or augments) a hook on the given member. Returns an Unhook
    // handle whose unhook() detaches this callback from dispatch.
    public static Unhook hookMethod(Member hookMember, XC_MethodHook callback) {
        if (hookMember == null) {
            throw new IllegalArgumentException("hookMethod: null member");
        }
        if (callback == null) {
            throw new IllegalArgumentException("hookMethod: null callback");
        }
        if (!(hookMember instanceof Method) && !(hookMember instanceof Constructor)) {
            throw new IllegalArgumentException(
                    "hookMethod: only Method/Constructor supported, got " + hookMember);
        }

        LspHooker hooker;
        boolean install = false;
        synchronized (HOOKERS) {
            hooker = HOOKERS.get(hookMember);
            if (hooker == null) {
                boolean isStatic = Modifier.isStatic(hookMember.getModifiers());
                hooker = new LspHooker(hookMember, isStatic);
                HOOKERS.put(hookMember, hooker);
                install = true;
            }
        }
        hooker.addCallback(callback);

        if (install) {
            try {
                Method backup = (Method) nativeHookMethod(
                        hookMember, hooker, CALLBACK_METHOD);
                if (backup == null) {
                    HOOKERS.remove(hookMember);
                    hooker.removeCallback(callback);
                    throw new IllegalStateException(
                            "nativeHookMethod returned null for " + hookMember);
                }
                hooker.setBackup(backup);
            } catch (Throwable t) {
                HOOKERS.remove(hookMember);
                hooker.removeCallback(callback);
                log("hookMethod failed for " + hookMember + ": " + t);
                throw new RuntimeException(t);
            }
        }
        return new Unhook(hooker, callback);
    }

    public static void log(String message) {
        Log.i(TAG, message);
    }

    public static void log(Throwable t) {
        Log.e(TAG, Log.getStackTraceString(t));
    }

    // Native binding: installs the hook via lsplant::Hook and returns the
    // backup (original) Method. Implemented in java_hooks.cpp.
    private static native Object nativeHookMethod(
            Member target, Object hooker, Method callback);

    // Handle returned by hook calls. unhook() detaches this callback from
    // dispatch (it does NOT un-install the underlying LSPlant hook).
    public static final class Unhook {
        private final LspHooker hooker;
        private final XC_MethodHook callback;

        Unhook(LspHooker hooker, XC_MethodHook callback) {
            this.hooker = hooker;
            this.callback = callback;
        }

        public Member getHookedMethod() {
            return hooker.getHookedMember();
        }

        public XC_MethodHook getCallback() {
            return callback;
        }

        public void unhook() {
            hooker.removeCallback(callback);
        }
    }
}
