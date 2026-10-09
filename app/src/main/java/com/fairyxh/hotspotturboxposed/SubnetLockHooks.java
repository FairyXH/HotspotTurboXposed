package com.fairyxh.hotspotturboxposed;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;

/**
 * 锁热点 DHCP 网段。
 *
 * hook 点（已在设备上通过 dex 分析确认存在）：
 *  - TetheringConfiguration.getLegacyDhcpRanges()
 *  - TetheringConfiguration.findAvailablePrefixFromRange(...)
 *  - TetheringConfiguration.getDownstreamPrefix(...)
 *  - TetheringConfiguration.isRandomPrefixBaseEnabled()
 *
 * 基于 libxposed API 102：Hooker = XposedInterface$Hooker（intercept 返回 Object），
 * 无注解，直接返回自定义值即可覆盖原方法返回值。
 */
public final class SubnetLockHooks {

    private static final String TETHERING_CFG = "com.android.networkstack.tethering.TetheringConfiguration";

    public static void install(XposedModule module) {
        if (!HotspotConfig.lockSubnet) {
            Entry.log("锁网段功能未启用，跳过");
            return;
        }
        try {
            Class<?> cfgClass = Class.forName(TETHERING_CFG);
            Entry.log("找到 TetheringConfiguration: " + cfgClass.getName());

            // 1) hook isRandomPrefixBaseEnabled → 强制 false（避免随机前缀偏移）
            hookMethod(module, cfgClass, "isRandomPrefixBaseEnabled", new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Entry.log("isRandomPrefixBaseEnabled → false (禁用随机前缀)");
                    return false;
                }
            });

            // 2) hook findAvailablePrefixFromRange → 直接返回目标前缀
            hookMethod(module, cfgClass, "findAvailablePrefixFromRange", new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Entry.log("findAvailablePrefixFromRange → " + HotspotConfig.prefix);
                    return HotspotConfig.prefix;
                }
            });

            // 3) hook getLegacyDhcpRanges → 返回自定义范围
            hookMethod(module, cfgClass, "getLegacyDhcpRanges", new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Entry.log("getLegacyDhcpRanges → [" + HotspotConfig.dhcpStart + "," + HotspotConfig.dhcpEnd + "]");
                    return new String[]{HotspotConfig.dhcpStart, HotspotConfig.dhcpEnd};
                }
            });

            // 4) hook getDownstreamPrefix → 返回目标前缀
            hookMethod(module, cfgClass, "getDownstreamPrefix", new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Entry.log("getDownstreamPrefix → " + HotspotConfig.prefix);
                    return HotspotConfig.prefix;
                }
            });

            Entry.log("锁网段 hook 已全部注册");
        } catch (Throwable t) {
            Entry.log("锁网段 hook 注册失败: " + t);
        }
    }

    /** 按名字找方法并注册 hook */
    private static void hookMethod(XposedModule module, Class<?> cls, String name, XposedInterface.Hooker hooker) {
        try {
            Method m = findMethod(cls, name);
            if (m == null) {
                Entry.log("未找到方法: " + name);
                return;
            }
            m.setAccessible(true);
            module.hook(m).intercept(hooker);
            Entry.log("hook " + name + " ok");
        } catch (Throwable t) {
            Entry.log("hook " + name + " err: " + t);
        }
    }

    /** 按名字找方法（兼容参数变化） */
    private static Method findMethod(Class<?> cls, String name) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }
}