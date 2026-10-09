package com.fairyxh.hotspotturboxposed;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Hook 注册管理器。
 *
 * 基于实测（一加13T / ColorOS / Tethering APEX）确认的 hook 点：
 *
 * 锁网段链路：
 *   SoftApConfiguration
 *     → TetheringConfiguration（读 legacyDhcpRanges = config_tether_dhcp_range）
 *     → Tethering.makeDhcpServer() / IpServer.configureDhcp()
 *     → netd 模板 --dhcp-range=%s,%s,1h → /system/bin/dnsmasq
 *
 * 关键类：
 *   com.android.networkstack.tethering.TetheringConfiguration
 *   com.android.networkstack.tethering.Tethering
 *   com.android.networkstack.tethering.IpServer
 */
public final class HookManager {

    public static void install(XposedModule module) {
        // 1. 锁网段：hook TetheringConfiguration 的网段选择
        SubnetLockHooks.install(module);

        // 2. 网络优化
        NetworkOptimizer.install(module);

        // 3. 双频合一（实验性）
        BridgedSoftApHooks.install(module);
    }
}