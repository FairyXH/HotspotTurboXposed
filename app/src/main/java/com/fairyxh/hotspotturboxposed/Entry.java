package com.fairyxh.hotspotturboxposed;

import io.github.libxposed.api.XposedModule;

/**
 * Hotspot Turbo Xposed 模块入口（libxposed API 101）。
 *
 * 入口由 META-INF/xposed/java_init.list 声明，由框架实例化并调用回调。
 *
 * 功能：
 * 1. 锁热点 DHCP 网段（hook TetheringConfiguration）
 * 2. 热点网络优化（速率/延迟参数）
 * 3. 探索双频合一热点（BridgedSoftAp）
 */
public class Entry extends XposedModule {
    private static final String TAG = "HTXposed";
    private static final String TARGET_PACKAGE = "com.android.networkstack";
    private static boolean hooksInstalled = false;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log("Hotspot Turbo Xposed 模块已加载, 版本 1.0.0");
        log("processName: " + param.getProcessName());
        log("systemServer: " + param.isSystemServer());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        log("onPackageLoaded: " + pkg);
        // 只在 networkstack 进程安装 hook（TetheringConfiguration 所在进程）
        if (!TARGET_PACKAGE.equals(pkg)) {
            log("非目标进程，跳过 hook: " + pkg);
            return;
        }
        if (hooksInstalled) {
            log("hooks 已安装，跳过");
            return;
        }
        hooksInstalled = true;
        try {
            HotspotConfig.load();
            HookManager.install(this);
            log("HookManager.install 完成");
        } catch (Throwable t) {
            log("HookManager.install 失败: " + t);
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        log("system_server 启动中");
        // system_server 不安装 hooks（Tethering 逻辑在 networkstack 进程）
    }

    public static void log(String msg) {
        android.util.Log.i(TAG, msg);
    }
}