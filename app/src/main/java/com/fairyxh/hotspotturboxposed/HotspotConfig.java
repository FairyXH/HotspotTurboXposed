package com.fairyxh.hotspotturboxposed;

/**
 * 热点配置，可从 persist. 属性或 SharedPreferences 读取。
 * 默认：锁 192.168.66.0/24，网关 .1，DHCP 范围 .10-.200。
 */
public final class HotspotConfig {

    // 目标网段（默认 192.168.66.0/24）
    public static String prefix = "192.168.66.0/24";
    public static String gateway = "192.168.66.1";
    public static String dhcpStart = "192.168.66.10";
    public static String dhcpEnd = "192.168.66.200";

    // 是否锁定网段
    public static boolean lockSubnet = true;
    // 是否开启双频合一（实验性）
    public static boolean bridgedSoftAp = false;
    // 是否启用网络优化参数
    public static boolean netOptimize = true;

    public static void load() {
        try {
            String prefixProp = getProp("persist.htxposed.prefix");
            if (prefixProp != null) prefix = prefixProp;
            String gwProp = getProp("persist.htxposed.gateway");
            if (gwProp != null) gateway = gwProp;
            String startProp = getProp("persist.htxposed.dhcp_start");
            if (startProp != null) dhcpStart = startProp;
            String endProp = getProp("persist.htxposed.dhcp_end");
            if (endProp != null) dhcpEnd = endProp;
            String lockProp = getProp("persist.htxposed.lock_subnet");
            if (lockProp != null) lockSubnet = "1".equals(lockProp) || "true".equals(lockProp);
            String bridgeProp = getProp("persist.htxposed.bridged_softap");
            if (bridgeProp != null) bridgedSoftAp = "1".equals(bridgeProp) || "true".equals(bridgeProp);
            String optProp = getProp("persist.htxposed.net_optimize");
            if (optProp != null) netOptimize = "1".equals(optProp) || "true".equals(optProp);
            logConfig();
        } catch (Throwable t) {
            Entry.log("HotspotConfig.load error: " + t);
        }
    }

    private static String getProp(String name) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = clazz.getMethod("get", String.class);
            Object val = m.invoke(null, name);
            if (val != null && ((String) val).length() > 0) {
                return (String) val;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void logConfig() {
        Entry.log("配置: lockSubnet=" + lockSubnet
                + " prefix=" + prefix
                + " gateway=" + gateway
                + " dhcp=" + dhcpStart + "-" + dhcpEnd
                + " bridgedSoftAp=" + bridgedSoftAp
                + " netOptimize=" + netOptimize);
    }
}