package com.devicespooflab.hooks.xposed;

import android.content.pm.ApplicationInfo;

/**
 * Drop-in shim cho de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam.
 * Chỉ giữ các field mà các lớp hooks/ thực sự dùng: classLoader, packageName,
 * processName, appInfo. Không kế thừa XC_LoadPackage và không có behaviour
 * callback nào — đây thuần là một POJO context truyền vào hook() của mỗi lớp.
 */
public class LoadPackageParam {
    public ClassLoader classLoader;
    public String packageName;
    public String processName;
    public ApplicationInfo appInfo;
}
