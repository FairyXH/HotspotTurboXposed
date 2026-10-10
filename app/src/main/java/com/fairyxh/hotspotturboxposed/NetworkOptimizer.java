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

    /**
     * 在 system_server 中应用系统级网络优化。
     * system_server 具有 system 权限（可读 sysctl），尝试直接写 /proc/sys
     * 以及通过 su 提升到 root 写 sysctl。
     * 与 hotspot_turbo / net_turbo_pro 保持一致的全局最优值。
     */
    public static void applySystemServerOptimize() {
        // 1) 先尝试直接写 /proc/sys（部分值 system 可写）
        writeProcSys("net/core/rmem_max", "33554432");
        writeProcSys("net/core/wmem_max", "33554432");
        writeProcSys("net/ipv4/tcp_rmem", "8192 2097152 33554432");
        writeProcSys("net/ipv4/tcp_wmem", "8192 2097152 33554432");
        writeProcSys("net/ipv4/tcp_notsent_lowat", "524288");
        writeProcSys("net/ipv4/tcp_mtu_probing", "1");
        // 2) 再尝试 su -c sysctl 覆盖（system_server 内 su 通常可用）
        runSu("sysctl -w net.core.rmem_max=33554432");
        runSu("sysctl -w net.core.wmem_max=33554432");
        runSu("sysctl -w net.ipv4.tcp_rmem=\"8192 2097152 33554432\"");
        runSu("sysctl -w net.ipv4.tcp_wmem=\"8192 2097152 33554432\"");
        runSu("sysctl -w net.ipv4.tcp_notsent_lowat=524288");
        runSu("sysctl -w net.ipv4.tcp_mtu_probing=1");
        // 3) 验证
        verifyOptimize();
    }

    private static void writeProcSys(String path, String value) {
        try {
            java.io.File f = new java.io.File("/proc/sys/" + path);
            if (f.exists()) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                fos.write(value.getBytes());
                fos.close();
                Entry.log("proc sysctl 写入: " + path + " = " + value);
            } else {
                Entry.log("proc sysctl 路径不存在: " + path);
            }
        } catch (Throwable t) {
            Entry.log("proc sysctl 写入失败: " + path + " err=" + t.getMessage());
        }
    }

    private static void verifyOptimize() {
        String[] keys = {"net.core.rmem_max", "net.core.wmem_max", "net.ipv4.tcp_notsent_lowat", "net.ipv4.tcp_mtu_probing"};
        StringBuilder sb = new StringBuilder("sysctl 验证:");
        for (String k : keys) {
            try {
                Process p = new ProcessBuilder("sysctl", "-n", k).redirectErrorStream(true).start();
                byte[] buf = new byte[128];
                int n = p.getInputStream().read(buf);
                String v = new String(buf, 0, Math.max(n, 0)).trim();
                sb.append(" ").append(k).append("=").append(v);
            } catch (Throwable t) {
                sb.append(" ").append(k).append("=?");
            }
        }
        Entry.log(sb.toString());
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