package com.fairyxh.hotspotturboxposed;

import io.github.libxposed.api.XposedModule;

import java.io.File;

/**
 * 热点网络优化。
 *
 * 与 hotspot_turbo / net_turbo_pro 配合：
 *  - 在热点启动时应用 sysctl 网络调优参数（与已有模块保持一致的最优值）
 *  - 通过 hook 关键路径做性能增强（预留）
 *
 * 已确认的统一最优值（来自 net_turbo + hotspot_turbo 重叠参数）：
 *   tcp_rmem/wmem = 8192 2097152 33554432
 *   core.rmem_max/wmem_max = 33554432
 *   tcp_notsent_lowat = 524288
 */
public final class NetworkOptimizer {

    private static final String TAG = "HTXposed";

    public static void install(XposedModule module) {
        if (!HotspotConfig.netOptimize) {
            Entry.log("网络优化未启用，跳过");
            return;
        }
        try {
            // 尝试通过 root 写入 sysctl（需要 su）
            applySysctl();
            Entry.log("网络优化 sysctl 已应用");
        } catch (Throwable t) {
            Entry.log("网络优化应用失败: " + t);
        }
    }

    private static void applySysctl() {
        // 尝试使用 su 写入（模块进程可能无 root，需 su -c）
        runSu("sysctl -w net.core.rmem_max=33554432");
        runSu("sysctl -w net.core.wmem_max=33554432");
        runSu("sysctl -w net.ipv4.tcp_rmem=\"8192 2097152 33554432\"");
        runSu("sysctl -w net.ipv4.tcp_wmem=\"8192 2097152 33554432\"");
        runSu("sysctl -w net.ipv4.tcp_notsent_lowat=524288");
        runSu("sysctl -w net.ipv4.tcp_mtu_probing=1");
    }

    private static void runSu(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            p.waitFor();
            Entry.log("su: " + cmd + " exit=" + p.exitValue());
        } catch (Throwable t) {
            Entry.log("su 执行失败: " + cmd + " err=" + t);
        }
    }
}