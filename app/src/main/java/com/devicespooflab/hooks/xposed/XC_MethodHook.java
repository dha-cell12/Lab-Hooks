package com.devicespooflab.hooks.xposed;

import java.lang.reflect.Member;

// Drop-in replacement for de.robv.android.xposed.XC_MethodHook, backed by
// LSPlant instead of the Xposed framework. The hook classes under hooks/
// subclass this and override beforeHookedMethod / afterHookedMethod with the
// same semantics they relied on under Xposed.
public abstract class XC_MethodHook {

    // Mirrors Xposed's MethodHookParam: carries the call context to and from
    // the before/after callbacks. Populated by the LSPlant hooker dispatch.
    public static final class MethodHookParam {
        // The hooked member (Method or Constructor).
        public Member method;
        // The receiver (null for static methods/constructors-as-called).
        public Object thisObject;
        // The call arguments; before-callbacks may overwrite entries.
        public Object[] args;

        private Object result;
        private Throwable throwable;
        private boolean resultSet;
        private boolean returnEarly;

        public Object getResult() {
            return result;
        }

        // Setting a result from beforeHookedMethod short-circuits the original
        // method; from afterHookedMethod it replaces the return value.
        public void setResult(Object result) {
            this.result = result;
            this.resultSet = true;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.resultSet = false;
            this.returnEarly = true;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }

        // ---- Dispatch-internal accessors (used by the LSPlant hooker) ----

        public boolean isResultSet() {
            return resultSet;
        }

        public boolean shouldReturnEarly() {
            return returnEarly;
        }

        public void resetReturnEarly() {
            this.returnEarly = false;
        }
    }

    // Called before the original method. Override to inspect/modify args or to
    // short-circuit via param.setResult / param.setThrowable.
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    // Called after the original method. Override to inspect/replace the result.
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    // ---- Dispatch entry points (called by the LSPlant hooker object) ----

    public final void callBefore(MethodHookParam param) throws Throwable {
        beforeHookedMethod(param);
    }

    public final void callAfter(MethodHookParam param) throws Throwable {
        afterHookedMethod(param);
    }
}
