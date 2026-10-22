# 阶段 2 测试记录

日期：2026-10-19
版本：0.2.0-debug (versionCode 2)
环境：Windows 11；JDK 17.0.13；Gradle 8.9；Android SDK 35；build-tools 35.0.0

## 构建

| 项 | 命令 | 结果 |
| --- | --- | --- |
| Debug 构建 | `.\gradlew.bat :app:assembleDebug` | 通过 |
| Lint | `.\gradlew.bat :app:lintDebug` | 通过，0 issue |
| 协议单测 | `.\gradlew.bat :protocol:test` | 19/19 通过 |
| Core 单测 | `.\gradlew.bat :core:testDebugUnitTest` | 3/3 通过 |
| Transport 单测 | `.\gradlew.bat :transport:testDebugUnitTest` | 2/2 通过 |

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
58.52 MB
package com.lumalink.g100d, version 0.2.0-debug (2)
minSdk 29, targetSdk 35, compileSdk 35
foregroundServiceType=dataSync
usesCleartextTraffic=true
```

## 自动化测试明细

| 测试类 | 数量 | 内容 |
| --- | --- | --- |
| `LumixEndpointsTest` | 3 | 候选端点 URL |
| `ReadOnlyGuardTest` | 4 | GET/HEAD 白名单、控制命令拦截、公网拦截、私有地址判断 |
| `LumixBrowseParserTest` | 3 | 属性/子元素/文件夹/日期/文件名回退 |
| `LumixStatusParserTest` | 3 | 电量、模式、录像、剩余张数、电量格数不误判 |
| `LumixHttpProtocolTest` | 6 | MockWebServer 握手、getinfo 缺失回退、状态、浏览、只读拦截、文件流 |
| `FormattersTest` | 3 | 字节/速度格式化 |
| `TransferItemTest` | 2 | 进度/ETA/未知大小 |

## 用户真机验证

- 阶段 1 APK：用户已安装，能启动，页面切换正常。
- 2026-10-19：用户用 v0.3.1 成功连接 G100D；`getinfo`/`getstate` 均 200，握手成功（LUMIX G100D，固件 1.0），`bindProcessToNetwork=true`。
- 目录浏览：`/DCIM` 200 但显示 0 个文件；已用 v0.4.1 修复 `<dir>` 解析、目录参数回退并增加响应体日志，待复测。
- 传输/下载：尚未验证。

## 未验证项

- 真实 G100D Wi-Fi 绑定、系统弹窗、握手与文件列表
- 文件下载、Range 续传、MediaStore 保存
- 前台服务在 HyperOS 后台的存活与通知
- 1GB 大文件速度、失败重试、完整性校验
- 小米/荣耀/vivo/OPPO/三星兼容性矩阵

## 说明

- `usesCleartextTraffic=true` 用于相机本地 HTTP；`HostGuard` 只允许私有/链路本地地址。
- 没有向真实相机发送任何请求；协议测试全部使用 MockWebServer。
