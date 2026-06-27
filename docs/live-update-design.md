# Live-update design (Hướng E — deferred)

This document records the design for runtime, restart-free config updates. It is
**not implemented yet**. The current behavior is restart-based (Hướng D): config
edits take effect on the target app's next launch.

## Why restart-based is the current model

The spoof profile lives in the module app's sandbox
(`/data/data/com.devicespooflab.hooks/files/device_profile.conf`). Two hard
constraints make runtime self-refresh impossible with the simple approach:

1. **Only root can read the profile.** The path sits in the module app's private
   data dir (`0700` traversal). A hooked target process runs under its own UID
   and cannot traverse into another package's sandbox. `setReadable(true,false)`
   on the file does not open directory traversal.
2. **The companion is only reachable pre-specialize.** Zygisk's
   `connectCompanion()` works only in `pre[App|Server]Specialize`, while the
   process still has zygote privilege. After specialization (i.e. at runtime,
   when a poll loop would run), SELinux blocks opening a new companion socket,
   and the `Api*` handle is no longer valid.

Together these mean a hooked process cannot, on its own, pull a fresh profile
after startup. The profile is loaded once in `postAppSpecialize` and stays for
the process lifetime.

## Hướng E — long-lived companion fd (the restart-free approach)

The idea: keep a companion connection open past specialization and use it for
periodic re-reads, instead of trying to reconnect at runtime.

### Protocol changes

The current wire protocol (`companion_protocol.h`) is one-shot: one `int32`
request, one `int32 count` + payload, then close. Hướng E needs a multi-round
protocol on a single fd:

```
client -> companion:  int32 request   (REQ_PROFILE / REQ_SCOPE / REQ_GENERATION)
companion -> client:  int32 count + pairs   (as today)
... repeated any number of times on the same fd ...
```

Add a lightweight `REQ_GENERATION` that returns just the profile's mtime or a
hash, so the client can cheaply check "did anything change?" before pulling the
full profile.

### Module changes

1. In `preAppSpecialize`, after the initial profile pull, **do not close** the
   companion fd. Hand it to the native layer and use `exemptFd()` so Zygote does
   not close it during specialization.
2. Spawn a native poller thread (or reuse the Java poll loop calling down via
   JNI) that periodically sends `REQ_GENERATION` on the retained fd.
3. When the generation advances, send `REQ_PROFILE`, rebuild `g_props`, and
   re-apply: re-run the relevant value caches and call
   `BuildHooks.refreshStaticFields` on the Java side.

### Risks / open questions

- **fd longevity:** the companion side may close the socket; the retained fd can
  go stale. Need a reconnect-is-impossible fallback (degrade to restart-based).
- **exemptFd semantics:** confirm an exempted companion fd survives
  specialization cleanly and is not flagged by detection.
- **Thread safety:** `g_props` is currently load-once/read-only after install
  (see `ds_state.h`). A live refresh makes it mutable; every reader
  (`LookupProperty`, the property hooks) would need a lock or RCU-style swap.
- **system_server:** holding a long-lived companion fd in `system_server` is
  higher risk; may want to keep system_server restart-based even under Hướng E.

### Why deferred

Hướng E touches the wire protocol, fd lifecycle, native threading, and the
immutability assumption of `g_props`. It is a self-contained feature worth doing
deliberately, not as a patch mid-migration. Restart-based update (Hướng D) is
correct and matches how most Zygisk modules behave.
