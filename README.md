> **本仓库为 MIUI14 适配 Mod 分支（HyperOriG Mod）**
>
> - 基于原作者的 [HyperOriG](https://github.com/KiriChen-Wind/HyperOriG) 修改，已获原作者同意。
> - 与原版区分：应用显示名为 `HyperOriG Mod`，模块版本号为 `2.1.0-Mod`（versionCode `260912`）。

为 NickHCK YuanDao OriG in 耳机接入 Xiaomi HyperOS 的 LSPosed 模块。

### 系统要求

- 接入模式：运行 **Xiaomi HyperOS 3** ，且使用 **LSPosed 101 API**
- 独立模式：任意 Android 15+ 设备（未经过测试）
- **MIUI14 / Android 13（接入模式，未充分测试）**：已放宽 `minSdk` 至 33 并加入降级逻辑——HyperOS 走焦点岛，MIUI14 回退到普通 heads-up 通知；HyperOS 专有类缺失时安全跳过，不会崩溃。需使用 **LSPosed 101 API**。

### Mod 版主要改动（相对原版）

**MIUI14 / Android 13 适配**

- `minSdk` 35 → 33，可在 Android 13 上安装。
- `HeadsetStateDispatcher` 同时兼容 Android 13 的 `A2dpService.connectionStateChanged(...)` 与 HyperOS 的 `handleConnectionStateChanged(...)`；回调整体容错，避免在蓝牙进程抛异常导致 `com.android.bluetooth` 崩溃。
- HyperOS 专有类缺失时安全跳过（`NoClassDefFoundError` / `NoSuchMethodError` 均被隔离）。
- 修复 Android 13 上的 `MediaRouter2.ScanRequest`（API 34+）与 `AdapterService.setBatteryLevel` 等 API 兼容问题。

**通知**

- 无焦点岛的系统（MIUI14）自动回退到普通 heads-up 通知（电量 + 降噪循环 + 断开）。
- 通知内容未变化时不重复发布（配合 `setOnlyAlertOnce`），修复每 30s 电量轮询导致的重复弹窗。

**电量显示**

- 以最新协议帧为准：未上报的耳/盒不再显示为“已连接”，修复“只连一只耳机却显示全部电量”。

**降噪状态**

- 修复切到通透后仍显示“抗风噪”：切离抗风噪时同步关闭耳机端开关，并在抗风噪开启时也采信明确模式回应。
- 记住上次使用的降噪子模式（普通 / 深度 / 实验性），切换回降噪时恢复该档位，而不是固定回到“普通”。

**运行日志**

- 内置跨进程运行日志（App / 蓝牙 / MiLink 等所有被注入的进程），App 设置页可**查看 / 复制 / 分享导出**，无需 root、无需 LSPosed 管理器。
- 自动捕获未处理异常堆栈，便于反馈问题。

**其它**

- 修复设置页返回闪退（`SnapshotStateList.removeLast()` 在 Android 13 不可用）。
- 文案“试验性降噪”统一为“实验性降噪”。

### 构建

在 **WSL** 中执行（`build_apk.sh` 使用 `/mnt/c` 路径）：

```bash
cd /mnt/c/Users/DX/Desktop/WSL/Project/hyperorig
bash build_apk.sh
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 引用以下项目

- HyperPods https://github.com/Art-Chen/HyperPods
- OppoPods https://github.com/Leaf-lsgtky/OppoPods
- OppoPods-Enhanced https://github.com/1812z/OppoPods
- NiceHCK Controller https://github.com/ZaeXT/NiceHCK_Controller
- Miuix https://github.com/compose-miuix-ui/miuix
- LibXposed API https://github.com/libxposed/api
