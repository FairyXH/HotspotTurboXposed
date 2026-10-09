# HotspotTurboXposed

基于 LSPosed API 102（libxposed）的 Android 热点加速与网段锁定 Xposed 模块。

## 功能

1. **锁热点 DHCP 网段**：通过 hook `TetheringConfiguration` 强制热点落在指定网段
   - `getLegacyDhcpRanges()` → 自定义 DHCP 起止范围
   - `findAvailablePrefixFromRange()` → 固定前缀
   - `getDownstreamPrefix()` → 固定网关前缀
   - `isRandomPrefixBaseEnabled()` → false（禁用随机前缀偏移）
2. **网络优化**：热点启动时应用 sysctl 调优参数（与 hotspot_turbo/net_turbo_pro 保持一致的最优值）
   - tcp_rmem/wmem = 8192 2097152 33554432
   - core.rmem_max/wmem_max = 33554432
   - tcp_notsent_lowat = 524288
3. **双频合一热点（实验性）**：hook `SoftApManager` 的 BridgedMode 相关开关

## 实测环境（一加13T / ColorOS 15）

```
wlan2 (AP)     : 192.168.56.122/24
dnsmasq 进程   : /system/bin/dnsmasq（用户 dns_tether，由 tethering 动态拉起）
dnsmasq 参数   : --dhcp-range=%s,%s,1h（netd 模板，范围由上层传入）
网段候选池     : legacyDhcpRanges = config_tether_dhcp_range
               [192.168.42.2~254] ~ [192.168.49.2~254]
Tethering APK  : /apex/com.android.tethering/priv-app/Tethering/Tethering.apk
关键类        : com.android.networkstack.tethering.TetheringConfiguration
               com.android.networkstack.tethering.Tethering
               进程 com.android.networkstack.process (NetworkStack)
```

## 配置（persist 属性）

| 属性 | 默认值 | 说明 |
|---|---|---|
| `persist.htxposed.lock_subnet` | 1 | 是否锁定网段 |
| `persist.htxposed.prefix` | 192.168.66.0/24 | 目标前缀 |
| `persist.htxposed.gateway` | 192.168.66.1 | 网关 |
| `persist.htxposed.dhcp_start` | 192.168.66.10 | DHCP 起始 |
| `persist.htxposed.dhcp_end` | 192.168.66.200 | DHCP 结束 |
| `persist.htxposed.bridged_softap` | 0 | 双频合一（实验性） |
| `persist.htxposed.net_optimize` | 1 | 网络优化 |

## 构建

```bash
./gradlew assembleRelease
# 输出 app/build/outputs/apk/release/
```

## 安装

1. 安装 APK 到设备
2. LSPosed 中启用模块，作用域勾选：
   - `com.android.networkstack.process`
   - `com.android.networkstack`
   - `com.android.server`
3. 重启 NetworkStack 进程（或软重启）
4. 查看日志：`adb logcat -s HTXposed`

## 双频合一探索

- Android 13+ 原生支持 BridgedSoftAp（热点桥接），默认关闭
- 本模块 hook `SoftApManager` 的 BridgedMode 开关尝试开启
- 注意：需要驱动/固件支持，一加13T 是否支持待验证