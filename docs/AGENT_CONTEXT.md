# HotspotTurboXposed — Agent 接力上下文

> 最后更新：2026-10-10 14:58（用户指示终止本轮调试，整理接力文档）
> 仓库：`FairyXH/HotspotTurboXposed`（public）｜本地：`/root/HotspotTurboXposed`
> 目标：基于 LSPosed API 101 (libxposed) 的 Xposed 模块：锁热点 DHCP 网段 192.168.66.0/24 + 网络优化 + 双频合一热点探索

---

## 0. 铁律与关键约束（用户明确要求，必须遵守）

1. **重启一律用 `reboot` 命令**。禁止软重启（emulated-soft-reboot.sh）、禁止 kill zygote/system_server 触发重启。此前违反导致 lspd 失联、状态判定异常。
2. **LSPosed 当前版本（2.2.0-it）已闭源**，GitHub 上的源码是老版本，**不要再花时间分析框架源码**。其他模块都能加载、只有我们的不行 → 问题在模块自身。
3. 不要伪造命令输出/测试结果。所有验证必须以真实工具输出为准。
4. 修改代码后必须验证；独立阶段完成后 commit。

---

## 1. 项目状态总览（核心结论）

**模块现状**：已被 LSPosed 完整识别（lspctl module show 显示 `Type: modern`、`Static scope: yes`、`API min=101 target=101`、User 0 enabled），scope 含 `system` + `com.android.networkstack`，db 三表（modules/modules_state/scope）记录齐全。
**核心未解决问题**：**运行时从不被任何进程注入加载**（system_server 和 networkstack 均无加载日志，连 "Failed to load" 都没有——而 ShortX 等失败模块有报错记录，说明我们在模块枚举/筛选阶段就被静默排除）。

**最新决定性事实（14:30-14:57 本轮确认）**：
- 清空 db 三表 + 全新安装后，LSPosed 自动扫描不会把我们的 APK 识别为模块（db 无记录）——除非**在 lspd 运行中重装 APK**（PACKAGE_ADDED 广播被捕获）才写入 modules 表。
- 但即使 modules/state/scope 三表齐全 + enabled=1 + 重启后，**依然零加载记录**（对比 NoDhcp 在 networkstack 加载 `Module loaded. runtimeApi=102`、Volumelimiter 在 system_server 加载）。
- 模块的 dex 结构、入口类结构、module.prop/java_init.list/scope.list 字节格式与 NoDhcp/Volumelimiter 几乎一致（详见 §4 对比数据）。

---

## 2. 最近一次会话的关键突破与转折（14:30 之后）

### 2.1 确认了 LSPosed 的"模块发现"机制（唯一有意义的发现）
- LSPosed 通过 **`ACTION_PACKAGE_ADDED/CHANGED` 广播**发现模块（daemon 内 `isModernModules` 检查 APK 内 `META-INF/xposed/java_init.list`）。
- **若安装 APK 时 lspd 未运行/未就绪，广播丢失 → 模块永远不会被写进 db**。
- 验证：lspd 运行中 `pm uninstall` + `pm install` → db modules 表自动出现记录、lspctl 立即显示该模块（之前全部手工插 db 的尝试因此是无效的）。
- **推论（待验证）**：此前所有"手工插 db + reboot"的验证轮次可能从未真正进入 LSPosed 的加载列表构建流程。14:44 重启后 db 记录仍在、模块 enabled，但依然不加载——说明还有更深的模块自身问题（见 §6 头号线索）。

### 2.2 用户方向纠正
- 用户明确：**不要看 LSPosed 源码**（闭源，网上是老版本），**从模块自身找问题**。
- 用户决策：终止本轮调试，整理接力文档，便于新 Agent 接手。

---

## 3. 已排除的假设（不要再重复排查）

| 假设 | 结论 |
|---|---|
| module.prop 格式（LF/CRLF） | ❌ 与 Volumelimiter 字节级一致（都是 LF），NoDhcp 是 CRLF，都能加载 |
| java_init.list 结尾无换行 | ❌ NoDhcp 无换行也能加载 |
| scope.list 含 system 无效 | ❌ Volumelimiter scope 就是 `system\n` 且正常加载 |
| 缺 v1 签名 | ❌ 两 APK 都只有 v2 签名，apksigner 结果一致 |
| dex 过大/压缩方式（STORED vs DEFLATED） | ❌ 与 NoDhcp 都是 classes.dex STORED，都能加载 |
| 入口类继承/无参构造/方法签名 | ❌ Entry 与 HookEntry 类结构一致（PUBLIC、继承 XposedModule、含 onModuleLoaded/onPackageLoaded/onSystemServerStarting） |
| 缺 META-INF/CERT.* | ❌ NoDhcp 有但 v1=false，非判定依据 |
| db 手工插入方式 | ❌ 改为广播发现后仍不加载 |
| scope 中 networkstack 进程启动早于注入 | ❌ NoDhcp 同样 scope networkstack 且成功加载 |
| APK 中打包了 libxposed service 类 / manifest 有 XposedProvider | ⚠️ 我们已移除，仍不加载。但**这可能反而错了**（见 §6） |

---

## 4. 关键对比数据（当前安装状态）

### 4.1 APK 元数据
| 项 | 我们 | NoDhcp | Volumelimiter（模板） |
|---|---|---|---|
| versionCode | 100 | 102 | 100 |
| minSdk/targetSdk | 30/36 | 29/37 | 30/36 |
| API | min=101 target=101 | min=101 target=101 | min=101 target=101 |
| dex 大小 | 2095632 B (STORED) | 2089692 B (STORED) | 2447672 B + 5 个附属 dex |
| 签名 | v2 only | v2 only | - |
| scope | networkstack+system | networkstack+google.networkstack | system |

### 4.2 三模块 META-INF/xposed 字节内容
- 我们 module.prop：`minApiVersion=101\ntargetApiVersion=101\nstaticScope=true\nexceptionMode=protective\n`（与 Volumelimiter 完全一致）
- 我们 java_init.list：`com.fairyxh.hotspotturboxposed.Entry\n`（与 Volumelimiter 格式一致）
- 我们 scope.list：`com.android.networkstack\nsystem`
- NoDhcp：module.prop 用 CRLF、java_init.list 无结尾换行、scope 无 system——**全都与我们有差异却都能加载** → 格式不是问题

### 4.3 入口类结构（dexdump 证实）
- 我们 `Entry`：PUBLIC、Superclass=`Lio/github/libxposed/api/XposedModule;`、无参构造、含 `onModuleLoaded(ModuleLoadedParam)`、`onPackageLoaded(PackageLoadedParam)`、`onSystemServerStarting(SystemServerStartingParam)`、静态 `log(String)`。
- NoDhcp `HookEntry`：同上结构（另有若干 private hook 方法与 lambda）。
- **结论：入口类结构完全合规。**

### 4.4 加载日志证据（14:37 重启后）
- NoDhcp：`(com.android.networkstack.process)[io.github.moranyue.nodhcphostname,...] Module loaded. runtimeApi=102`
- Volumelimiter：`(system)[io.github.fairyxh.volumelimiter,...] Module loaded in system_server; Android 15 (API 35)`
- 我们：**零记录**（modules/verbose 均无，无失败记录）。

---

## 5. 设备/LSPosed 环境事实（新 Agent 必读）

- LSPosed IT v2.2.0-it (7892)，API 102（支持运行 API 101 模块），Zygisk Next + KernelSU。
- 关键路径：
  - lspd 二进制：`/data/adb/modules/zygisk_lsposed/lspd`；daemon 脚本：`.../daemon`（`exec ./lspd`）
  - db：`/data/adb/lspd/config/modules_config.db`（表：modules/modules_state/scope/module_configs/lspd_configs/app_configs）
  - 日志：`/data/adb/lspd/log/{modules,verbose}_*.log`（只记录经 XposedBridge 的日志，不记录 android.util.Log；**模块枚举/筛选阶段的静默排除不会留下痕迹**）
  - lspctl：`/data/adb/modules/zygisk_lsposed/lspctl`，子命令 `status/module(list,show)/scope list/hook-debug dump`，**无 enable/disable/reload**
- db 表结构：`modules(module_pkg_name PRIMARY KEY, apk_path)`；`modules_state(module_pkg_name, user_id, enabled, scope_request_blocked)`；`scope(module_pkg_name, app_pkg_name, user_id)`
- 设备 dex 工具：`/apex/com.android.art/bin/dexdump`、`dalvikvm`（**无 javac、无 baksmali**）
- 当前安装路径：`/data/app/~~R9S5sTsg2OHNijMXkmhczA==/com.fairyxh.hotspotturboxposed-pMKSL2NkHKcz38F4f8eY-w==/base.apk`（14:35 重装）
- 已确认可加载模块（参照）：`io.github.moranyue.nodhcphostname`（networkstack）、`io.github.fairyxh.volumelimiter`（system_server）
- 设备还有大量其他模块正常加载（VirtualEnv/ZhangSystemHook 等），共 58 个模块。

---

## 6. 头号新线索（未验证，最可能的方向）

**`app/build.gradle.kts` 与能正常加载的模板 Volumelimiter 存在关键差异**（14:56 确认）：

| 配置项 | Volumelimiter（✅ 加载成功） | 我们（❌ 不加载） |
|---|---|---|
| isMinifyEnabled | **true**（R8 开启） | false（我们关闭了） |
| isShrinkResources | **true** | false |
| libxposed.service 依赖 | **implementation(libs.libxposed.service)** | 已移除（compileOnly api） |
| dex 结构 | classes.dex + classes2~6.dex（**多 dex**） | 单 classes.dex |
| R8 keep 规则 | proguard-rules.pro（需查看内容） | proguard-rules.pro（内容待对比） |

**推论**：此前的两个"修复"（关闭 R8、移除 service 依赖）可能走错了方向——Volumelimiter 恰恰是 R8 开启 + 带 service 依赖且能正常加载。且 Volumelimiter 有多个 dex（说明 AGP 自动多 dex 拆分，可能 LSPosed 对多 dex 模块的处理不同）。

**建议下一步（按优先级）**：
1. 直接复刻 Volumelimiter 的构建配置（isMinifyEnabled=true、isShrinkResources=true、加回 `implementation(libs.libxposed.service)`、检查 proguard-rules.pro 是否保留入口类），重新构建 → 安装（lspd 运行中）→ reboot → 查加载日志。
2. 对比 Volumelimiter 的 proguard-rules.pro 与我们的是否一致（R8 需要 keep Entry 类及其回调）。
3. 若仍不加载，将 NoDhcp 工程**原样构建**（改包名/入口类名/scope 为我们的）作为"最小可加载"基线，再逐步加回业务代码，二分定位是哪部分导致被排除。
4. 关注 Volumelimiter 的 AndroidManifest.xml（4436 B，明显比我们 1456 B 复杂，有 provider/launcher activity）——是否 LSPosed 对**带 provider 的模块**判定不同（此前我们以为 provider 是问题并移除了，但 Volumelimiter 有 provider 且能加载）。

---

## 7. 当前工作区状态

- Git：`68b1c19`（最后提交），工作区干净。提交历史见 §8。
- 构建链（已就绪，勿破坏）：
  - gradle wrapper 9.3.1 + AGP 9.0.1（`gradle/libs.versions.toml`）
  - **aapt2 需要 qemu wrapper**：`/opt/aapt2/aapt2`（`exec /usr/bin/qemu-x86_64-static -L /usr/x86_64-linux-gnu /opt/aapt2/aapt2-bin "$@"`），通过 `android.aapt2FromMavenOverride=/opt/aapt2/aapt2` 注入
  - `local.properties` 的 `sdk.dir=/opt/android-sdk`
  - 构建：`cd /root/HotspotTurboXposed && ./gradlew :app:assembleRelease --no-daemon`
  - 产物：`app/build/outputs/apk/release/app-release.apk`（约 2.1MB，debug 签名）
  - 镜像：`~/.gradle/init.gradle`（阿里云），settings.gradle.kts 内也有
- 源码结构：`app/src/main/java/com/fairyxh/hotspotturboxposed/`（Entry/HookManager/SubnetLockHooks/NetworkOptimizer/BridgedSoftApHooks/HotspotConfig）
- 资源：`app/src/main/resources/META-INF/xposed/`（module.prop/scope.list/java_init.list）
- 参照工程（勿删）：
  - `/root/Volumelimiter-template`（API 101 模板，同作者，✅ 能加载）
  - `/root/NoDhcpHostname`（API 101，✅ networkstack 加载）
  - `/root/LSPosed-src`（老版本源码，用户已指示不必再看）
- 设备临时文件：`/data/local/tmp/htx_cur.apk`、`nd_cur.apk`、`vl_cur.apk`、`/data/local/tmp/hc/classes.dex`、`/data/local/tmp/nc/classes.dex`

---

## 8. Git 提交历史（排查轨迹）

```
68b1c19 修复: 移除 libxposed-service 依赖 + 关闭 R8 + scope 纳入 system
d1ccdad 修复: ic_launcher.xml 引用 @drawable/ic_launcher_foreground
0523c77 重构: 以 Volumelimiter(API 101) 模板工程为骨架, 改造为 AGP 9.0.1 标准工程
c0f6f1d 修复: Entry 类与 NoDhcp 类结构完全一致, classes.dex STORED 存储
34c8116 修复: scope.list 移除无效包名 com.android.server
e78579e 修复: 彻底移除 libxposed api/service 类打包
b56af0b 修复: module.prop 声明改为 min/targetApiVersion=101
421faf5 构建: 采用纯 modern 模块结构 + 内嵌 libxposed api/service 类
dfb2a24 发布 v1.0.0 构建产物 APK（手工构建）
ba69458 初始版本：LSPosed API 102 热点加速与网段锁定模块
```

注意：`ba69458`~`421faf5` 是旧构建体系（API 102/versionCode=1/minSdk=29）阶段的提交，`0523c77` 之后是 Volumelimiter 模板（API 101）体系。

---

## 9. 验证方法（判定功能是否生效）

1. **加载判定**：`grep -iE 'hotspotturbo|fairyxh' /data/adb/lspd/log/modules_*.log`（最新文件）
   - 期望：`(system)[com.fairyxh.hotspotturboxposed,...] Module loaded...` 或 `onModuleLoaded`
2. **日志**：`logcat -d -s HTXposed`（Entry 用 `android.util.Log.i("HTXposed", ...)`，不会进 lspd modules 日志，只进 logcat）
3. **功能判定（最终标准，用户强调"直接看功能有没有生效"）**：热点 wlan2 网关 IP 是否为 192.168.66.x
   - `ip addr show | grep -A3 wlan2`（当前仍是 192.168.60.x 等 → 未生效）
   - 目标：HotspotConfig.prefix 默认 192.168.66.0/24、gateway 192.168.66.1、dhcp 192.168.66.10-200
4. **状态**：`/data/adb/modules/zygisk_lsposed/lspctl module show com.fairyxh.hotspotturboxposed`

---

## 10. 待办（新 Agent 接手顺序）

1. **【最高优先】验证 §6 头号线索**：让构建配置与 Volumelimiter 完全一致（R8 开 + shrink 开 + service 依赖 + 对比 proguard-rules.pro），重装（确保 lspd 运行中）+ reboot + 查加载日志。
2. 若仍失败，按 §6 建议 3 做"最小可加载基线"二分定位。
3. 成功后按 §9 验证热点网段 192.168.66.x 是否生效。
4. 记得每次修改 → 构建 → 验证 → commit 的循环；重启一律 reboot。
