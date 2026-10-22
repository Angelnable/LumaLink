# 阶段 1 测试记录

日期：2026-10-19
环境：Windows 11；JDK 17.0.13（Microsoft OpenJDK）；Gradle 8.9；Android SDK 35；build-tools 35.0.0
工程：`D:\DeepSeek Harness\projects\LumaLink`（交付副本：`D:\LumaLink`）

## 构建

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 工具链 | `java -version` / `gradle --version` | 通过 |
| Debug 构建 | `.\gradlew.bat :app:assembleDebug` | 通过 |
| Instrumentation 编译 | `.\gradlew.bat :app:assembleDebugAndroidTest` | 通过 |
| Lint | `.\gradlew.bat :app:lintDebug` | 通过，0 issue |

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
58.22 MB
package com.lumalink.g100d, version 0.1.0-debug (1)
minSdk 29, targetSdk 35, compileSdk 35
SHA-256 D6AB86CAD5E73A766B3FB356A96D2634CED618988449C18D3F422588FEF433D4
```

签名：`apksigner verify --verbose` 通过（APK Signature Scheme v2，single signer）。
启动 Activity：`com.lumalink.g100d.MainActivity`。

源码包：

```text
dist/LumaLink-0.1.0-src.zip
0.12 MB（约 137 个条目，不含 build/.gradle/.dev/local.properties）
SHA-256 见 dist/SHA256SUMS.txt
```

## 单元测试

| 模块 | 测试 | 结果 |
| --- | --- | --- |
| protocol | `LumixEndpointsTest`（3 个） | 通过 |
| core | `FormattersTest`（3 个） | 通过 |
| app | 暂无 JVM 测试 | NO-SOURCE |

总计：6 个测试通过。

## 用户真机验证

2026-10-19，用户反馈：APK 已安装到手机，**能正常启动，页面切换正常**。具体机型与系统版本待补充；批量传输、连接相机等阶段 2 功能尚未验证。

## Instrumentation

`NavigationSmokeTest` 已编译进 `app-debug-androidTest.apk`，但**未由我运行**：当前没有连接设备，也没有启动模拟器。用户已手动完成启动与导航验证。

## 未执行 / 未验证

- 真机安装、启动、手势导航、深色/浅色截图
- 相机连接、握手、文件浏览、传输
- 传输速度、RAW/视频完整性、LUT 导出、遥控
- 小米/荣耀/vivo/OPPO/三星兼容性实测

## 说明

- 构建日志中的 `Unable to initialize metrics ... C:\Users\Taoka\.android` 是 AGP analytics 在沙箱下无法写入用户目录的告警，不影响 APK 生成与签名。
- 由于没有设备，所有 UI 视觉效果仅通过代码审查，未做像素级验收。
