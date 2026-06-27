package com.devicespooflab.hooks.xposed;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

// The hooker object handed to lsplant::Hook. LSPlant invokes callback(args)
// in place of the target method; this class drives the Xposed-style
// before/after dispatch and calls the backup (original) method in between.
//
// LSPlant contract:
//   - callback signature must be: public Object callback(Object[] args)
//   - args[0] is the receiver for non-static methods; static methods have no
//     receiver placeholder.
//   - the backup method is stored on this object (set via setBackup after
//     lsplant::Hook returns) so callback() can invoke the original.
public final class LspHooker {

    private final List<XC_MethodHook> callbacks = new ArrayList<>();
    private final Member hookedMember;
    private final boolean isStatic;

    // Set to the backup Method returned by lsplant::Hook. Invoking it runs the
    // original (unhooked) implementation.
    private volatile Method backup;

    public LspHooker(Member hookedMember, boolean isStatic) {
        this.hookedMember = hookedMember;
        this.isStatic = isStatic;
    }

    public synchronized void addCallback(XC_MethodHook cb) {
        callbacks.add(cb);
    }

    // Stops a callback from receiving dispatch. Note: this does NOT un-install
    // the underlying LSPlant hook (the shim does not expose lsplant::UnHook);
    // the backup trampoline stays in place and the method remains hooked, but
    // this callback no longer participates in before/after dispatch.
    public synchronized void removeCallback(XC_MethodHook cb) {
        callbacks.remove(cb);
    }

    public void setBackup(Method backup) {
        this.backup = backup;
        if (backup != null) backup.setAccessible(true);
    }

    public Member getHookedMember() {
        return hookedMember;
    }

    // Entry point invoked by LSPlant in place of the target method.
    public Object callback(Object[] args) throws Throwable {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = hookedMember;

        Object receiver;
        Object[] callArgs;
        if (isStatic) {
            receiver = null;
            callArgs = args != null ? args : new Object[0];
        } else {
            receiver = (args != null && args.length > 0) ? args[0] : null;
            int n = (args != null && args.length > 0) ? args.length - 1 : 0;
            callArgs = new Object[n];
            if (n > 0) System.arraycopy(args, 1, callArgs, 0, n);
        }
        param.thisObject = receiver;
        param.args = callArgs;

        XC_MethodHook[] snapshot;
        synchronized (this) {
            snapshot = callbacks.toArray(new XC_MethodHook[0]);
        }

        int beforeIdx = 0;
        for (; beforeIdx < snapshot.length; beforeIdx++) {
            param.resetReturnEarly();
            try {
                snapshot[beforeIdx].callBefore(param);
            } catch (Throwable t) {
                param.setThrowable(t);
            }
            if (param.shouldReturnEarly()) {
                beforeIdx++;
                break;
            }
        }

        if (!param.shouldReturnEarly()) {
            try {
                Object result = invokeOriginal(receiver, callArgs);
                param.setResult(result);
                param.resetReturnEarly();
            } catch (Throwable t) {
                param.setThrowable(t);
                param.resetReturnEarly();
            }
        }

        for (int i = beforeIdx - 1; i >= 0; i--) {
            try {
                snapshot[i].callAfter(param);
            } catch (Throwable t) {
                param.setThrowable(t);
            }
        }

        return param.getResultOrThrowable();
    }

    private Object invokeOriginal(Object receiver, Object[] callArgs) throws Throwable {
        if (backup == null) {
            throw new IllegalStateException(
                    "LspHooker: backup method not set for " + hookedMember);
        }
        if (isStatic) {
            return backup.invoke(null, callArgs);
        }
        Object[] full = new Object[callArgs.length + 1];
        full[0] = receiver;
        System.arraycopy(callArgs, 0, full, 1, callArgs.length);
        return backup.invoke(null, full);
    }
}
