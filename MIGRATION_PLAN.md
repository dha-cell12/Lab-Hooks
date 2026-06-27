# Lab-Hooks: Migration LSPosed/Xposed -> Zygisk + LSPlant

> Tai lieu nay tom tat ke hoach va tien do migration de tiep tuc o session moi.
> Cap nhat lan cuoi: phien chuyen tu MainHook (LSPosed) sang HookEntry (Zygisk standalone).

## Muc tieu

Go bo hoan toan phu thuoc LSPosed/Xposed runtime. Module chay standalone tren Zygisk, hook Java qua LSPlant. Config den tu root companion qua native layer (`ds::g_props` -> `NativeHooks.getAllFromNative`), khong con RemotePreferences/SEND_BINDER.

## Kien truc 2 loai "Xposed" trong codebase

- **Shim noi bo (GIU LAI)**: `com.devicespooflab.hooks.xposed.*` — backed by LSPlant. Gom `XposedHelpers`, `XposedBridge`, `XC_MethodHook`, `LspHooker`, va `LoadPackageParam` (moi tao).
- **API LSPosed that (GO BO)**: `de.robv.android.xposed.*` runtime, `io.github.libxposed.*`, `XposedModuleImpl`, `XposedServiceBridge`.

## Luong moi (sau migration)

1. Zygisk `preAppSpecialize`/`preServerSpecialize`: companion gui scope.list + profile qua socket.
2. `postAppSpecialize`/`postServerSpecialize` -> `applyProfileAndHook()` (zygisk_entry.cpp):
   - `ds::g_props = profile`; `ds::InstallPropertyHooks()` (native PLT hooks).
   - `ds::InitLSPlant(env)` roi `ds::InstallJavaHooks(env)`.
3. `InstallJavaHooks` (java_hooks.cpp) — cau JNI C++ -> Java: goi `HookEntry.installAll()`.
4. `HookEntry.installAll()` dung `LoadPackageParam` tu `ActivityThread` static accessors, chay chuoi 19 hook classes.
5. Moi hook class goi shim `XposedHelpers.findAndHookMethod` -> `XposedBridge.nativeHookMethod` (JNI) -> `lsplant::Hook`.

## Hop dong JNI da xac minh

- `java_hooks.cpp:24` — `FindClass("com/devicespooflab/hooks/HookEntry")`.
- `java_hooks.cpp:30` — `GetStaticMethodID(entryClass, "installAll", "()V")`.
- `zygisk_entry.cpp:168` — `ds::InstallJavaHooks(env)` goi trong `applyProfileAndHook()`, chay o ca postAppSpecialize va postServerSpecialize.
- `XposedBridge.nativeHookMethod(target, hooker, callback)` -> `lsplant::Hook` (java_hooks.cpp:55-69). Cau Java->C++ da ton tai.

## TIEN DO

### Da lam (ghi len dia, xac minh qua tool result)

1. **java_hooks.cpp** — `InstallJavaHooks` da thay stub bang cau JNI goi `HookEntry.installAll()`, co xu ly loi day du (FindClass/GetStaticMethodID/ExceptionCheck/DeleteLocalRef). XAC MINH bang read (69 dong).
2. **xposed/LoadPackageParam.java** — POJO shim 4 field: `classLoader`, `packageName`, `processName`, `appInfo`. XAC MINH bang read (16 dong). Luu y: runtime LSPosed co 5 field (them `isFirstApplication`) nhung grep xac nhan KHONG file hooks/ nao dung field thu 5, nen 4 field la du.
3. **HookEntry.java** — da tao (~165 dong). `installAll()` static `()V`, idempotent qua co `sInstalled`. Dung `LoadPackageParam` tu `ActivityThread.currentPackageName/currentProcessName` + `Thread.currentThread().getContextClassLoader()` + `currentApplication().getApplicationInfo()` (cho phep null). Chuoi 19 hook dung thu tu MainHook cu, moi hook boc try/catch rieng. SDK gate (AppSetId>=30, Euicc>=28), config gate (AccountHooks), isOwnPackage skip (SystemProperties/Display/Locale/Native). Bo hoan toan nhanh Application.attach + XposedServiceBridge + onBinderReady.

### CHUA lam (con lai)

4. **Doi import 16 file hooks/** tu `de.robv.android.xposed.*` sang `com.devicespooflab.hooks.xposed.*`. Moi file can doi 4 import:
   - `de.robv.android.xposed.XC_MethodHook` -> `com.devicespooflab.hooks.xposed.XC_MethodHook`
   - `de.robv.android.xposed.XposedBridge` -> `com.devicespooflab.hooks.xposed.XposedBridge`
   - `de.robv.android.xposed.XposedHelpers` -> `com.devicespooflab.hooks.xposed.XposedHelpers`
   - `de.robv.android.xposed.callbacks.XC_LoadPackage` -> `com.devicespooflab.hooks.xposed.LoadPackageParam`
   - VA signature: `hook(XC_LoadPackage.LoadPackageParam lpparam)` -> `hook(LoadPackageParam lpparam)`
   - **CHU Y**: khong phai file nao cung dung ca 4. Vd BuildHooks dung ca 4. Phai kiem tra tung file truoc khi doi (mot so chi dung XposedHelpers).
   - 16 file: WebViewHooks, TelephonyHooks, SystemPropertiesHooks, SettingsHooks, PackageManagerHooks, PackageInfoHooks, NetworkHooks, MediaDrmHooks, HardwareHooks, EuiccHooks, AppSetIdHooks, AccountHooks, BuildHooks, CameraHooks, DisplayHooks, AdvertisingIdHooks. (Con SensorHooks, StorageHooks, BatteryHooks, InputDeviceHooks, LocaleHooks — kiem tra them: grep `lpparam\.` chi tra 16 file co dung lpparam, nhung cac file con lai co the van import XC_LoadPackage cho signature.)
5. **Go file Xposed that**:
   - `MainHook.java` — XOA (HookEntry da thay the).
   - `XposedModuleImpl.java` — XOA (extends `io.github.libxposed.api.XposedModule`, dung `de.robv...XC_LoadPackage`).
   - `utils/XposedServiceBridge.java` — XOA (RemotePreferences qua lspd, da chet).
6. **NativeHooks.java** — doi 5 loi goi `XposedBridge.log` (dong 42,46,89,96,100) sang `android.util.Log`, go `import de.robv.android.xposed.XposedBridge` (dong 10). Phan con lai da sach.
7. **ConfigManager.java** — xoa `readXSharedPreferences()` (dong 89-124, tham chieu `de.robv.android.xposed.XSharedPreferences`), va 2 loi goi cua no trong `init()` (dong 71) va `reload()` (dong 1405). Ca 2 deu co fallback `readConfigFile()` nen go an toan.
8. **Verify build** — chua biet command chinh xac. De xuat `gradlew assembleDebug`. Nen ghi command vao AGENTS.md sau khi xac nhan.

## Diem can luu y / rui ro

- **Thu tu build**: HookEntry truyen `xposed.LoadPackageParam` vao cac `hook(lpparam)`. Khi chua doi import 16 file (buoc 4), code CHUA bien dich duoc. Day la trang thai du kien. Phai hoan tat buoc 4 truoc khi build.
- **ConfigManager.loadFromRemotePreferences()** — ten ham con cu nhung than da chuyen sang doc qua `NativeHooks.getAllFromNative()` (companion -> native). Khong con goi XposedServiceBridge.
- **publishToRemotePreferences()** (ConfigManager:180) da la no-op tra false. MainActivity.publishIfWritable goi no van hop le.
- **appInfo co the null** trong HookEntry (Application.attach co the chua chay luc postAppSpecialize). PackageInfoHooks:88 da check null.
- **system_server**: HookEntry chay ca trong system_server (postServerSpecialize). packageName se null -> fallback sang processName. Can kiem tra cac hook co an toan trong hook system_server khong (mot so co the gay loop).
- **ElfImg trong lsplant_init.cpp** (~dong 154-164): truy cap section header khong kiem tra bien `map_size_`, rui ro SIGSEGV neu libart.so bi cat. Quan sat phu, dang them guard.

## Files lien quan

- `app/src/main/cpp/java_hooks.cpp` — cau JNI InstallJavaHooks + nativeHookMethod. DA SUA.
- `app/src/main/cpp/zygisk_entry.cpp` — entry Zygisk, applyProfileAndHook dong 153-176.
- `app/src/main/cpp/ds_state.h` — khai bao InitLSPlant, InstallPropertyHooks, g_props (KHONG khai bao InstallJavaHooks — no o java_hooks.cpp).
- `app/src/main/java/com/devicespooflab/hooks/HookEntry.java` — entry Java moi. DA TAO.
- `app/src/main/java/com/devicespooflab/hooks/xposed/LoadPackageParam.java` — shim. DA TAO.
- `app/src/main/java/com/devicespooflab/hooks/MainHook.java` — CAN XOA.
- `app/src/main/java/com/devicespooflab/hooks/XposedModuleImpl.java` — CAN XOA.
- `app/src/main/java/com/devicespooflab/hooks/utils/XposedServiceBridge.java` — CAN XOA.
- `app/src/main/java/com/devicespooflab/hooks/NativeHooks.java` — CAN SUA (logging).
- `app/src/main/java/com/devicespooflab/hooks/utils/ConfigManager.java` — CAN SUA (go XSharedPreferences).
- `app/src/main/java/com/devicespooflab/hooks/hooks/*.java` — 16+ file CAN DOI IMPORT.

## Buoc tiep theo o session moi

1. Grep `de.robv.android.xposed` toan bo `hooks/` de co danh sach chinh xac file + import can doi.
2. Doi import tung file (4 thay the moi file, kiem tra file nao dung gi truoc).
3. Xoa MainHook, XposedModuleImpl, XposedServiceBridge.
4. Sua NativeHooks (logging) va ConfigManager (XSharedPreferences).
5. Build verify, sua loi bien dich con lai.
6. Kiem tra cac caller cua MainHook/XposedServiceBridge ngoai cac file tren (vd MainActivity) truoc khi xoa.
