package com.fairyxh.hotspotturboxposed;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;

/**
 * 双频合一热点（Bridged SoftAP）实验性探索。
 *
 * Android 13+ 原生支持 BridgedSoftAp（热点桥接，默认关闭）。
 * 通过 hook SoftApManager 的 bridged 相关开关尝试开启：
 *   com.android.server.wifi.SoftApManager
 *     - isBridgedModeOpportunisticShutdownEnabled()
 *     - setBridgedModeOpportunisticShutdownEnabled(boolean)
 *
 * 注意：双频合一需要驱动/固件支持，且可能与其他 wifi 功能冲突，默认关闭。
 * 探索结果：本设备（一加13T / ColorOS）SoftApManager 是否存在 bridged 相关方法待验证。
 */
public final class BridgedSoftApHooks {

    private static final String SOFTAP_MGR = "com.android.server.wifi.SoftApManager";

    public static void install(XposedModule module) {
        if (!HotspotConfig.bridgedSoftAp) {
            Entry.log("双频合一未启用（默认关闭，实验性）");
            return;
        }
        try {
            Class<?> clazz = Class.forName(SOFTAP_MGR);
            Entry.log("找到 SoftApManager: " + clazz.getName());

            // hook isBridgedModeOpportunisticShutdownEnabled → true
            Method isEnabled = findMethod(clazz, "isBridgedModeOpportunisticShutdownEnabled");
            if (isEnabled != null) {
                isEnabled.setAccessible(true);
                module.hook(isEnabled).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Entry.log("isBridgedModeOpportunisticShutdownEnabled → true");
                        return true;
                    }
                });
                Entry.log("hook isBridgedModeOpportunisticShutdownEnabled ok");
            } else {
                Entry.log("未找到 isBridgedModeOpportunisticShutdownEnabled（驱动/框架不支持）");
            }

            // 尝试 setBridgedModeOpportunisticShutdownEnabled(true)
            Method setEnabled = findMethod(clazz, "setBridgedModeOpportunisticShutdownEnabled", boolean.class);
            if (setEnabled != null) {
                setEnabled.setAccessible(true);
                module.hook(setEnabled).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Entry.log("setBridgedModeOpportunisticShutdownEnabled 被调用, arg=" + chain.getArg(0));
                        return chain.proceed();
                    }
                });
                Entry.log("hook setBridgedModeOpportunisticShutdownEnabled ok");
            }

            Entry.log("双频合一 hook 已注册（若框架不支持则无效）");
        } catch (Throwable t) {
            Entry.log("双频合一 hook 注册失败: " + t);
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getDeclaredMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }
}