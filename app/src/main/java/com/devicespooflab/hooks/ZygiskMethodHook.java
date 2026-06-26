package com.devicespooflab.hooks;

import java.lang.reflect.Member;

public class ZygiskMethodHook {
    public void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    public void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result = null;
        public boolean resultSet = false;
        private Throwable throwable = null;

        public Object getResult() { return result; }
        public void setResult(Object result) {
            this.result = result;
            this.resultSet = true;
        }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable t) { this.throwable = t; }
        public boolean hasThrowable() { return throwable != null; }
    }
}
