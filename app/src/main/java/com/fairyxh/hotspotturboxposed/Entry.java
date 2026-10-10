package com.fairyxh.hotspotturboxposed;

import io.github.libxposed.api.XposedModule;

/**
 * Hotspot Turbo Xposed 模块入口（LSPosed API 102）。
 *
 * 入口通过 assets/xposed_init 声明，由框架实例化并调用 onModuleLoaded。
 *
 * 功能：
 * 1. 锁热点 DHCP 网段（hook TetheringConfiguration）
 * 2. 热点网络优化（速率/延迟参数）
 * 3. 探索双频合一热点（BridgedSoftAp）
 */
public class Entry extends XposedModule {

    private static final String TAG = "HTXposed";

    /**
     * API 102 无参构造，框架要求。模块初始化逻辑放在 onModuleLoaded 中。
     */
    public Entry() {
        super();
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log("Hotspot Turbo Xposed 模块已加载, 版本 1.0.0");
        log("processName: " + param.getProcessName());
        log("systemServer: " + param.isSystemServer());

        // 只有 networkstack / system_server 进程才需要 hook
        String proc = param.getProcessName() == null ? "" : param.getProcessName();
        if (!proc.contains("networkstack") && !proc.equals("system_server")) {
            log("非目标进程，跳过 hook: " + proc);
            return;
        }

        // 加载配置（从 persist 属性或默认值）
        HotspotConfig.load();

        // 注册 hook
        try {
            HookManager.install(this);
            log("HookManager.install 完成");
        } catch (Throwable t) {
            log("HookManager.install 失败: " + t);
        }
    }

    public static void log(String msg) {
        android.util.Log.i(TAG, msg);
    }
}