# 阶段 1 报告：工程骨架与 Material 3 UI

日期：2026-10-19
状态：完成（可编译 APK；真机启动待用户验证）

## 1. 本阶段改动范围

- 创建 GitHub 风格多模块 Gradle 工程：`app / core / ui / protocol / transport / gallery / remote / lut`
- 搭建便携构建环境：JDK 17、Gradle 8.9、Android SDK 35、build-tools 35.0.0（位于 `D:\LumaLink\.dev`）
- 实现 Material 3 设计系统：深/浅色、动态取色、字体、形状、通用组件
- 实现 5 个主页面与设置/关于二级页面
- 实现 DataStore 主题持久化与 `AppContainer` 构造器注入
- 接入 edge-to-edge、手势条 inset、预测性返回、启动屏、自适应图标
- 建立 JVM 单测、Compose instrumentation 测试骨架、GitHub Actions、构建/打包脚本
- 写入阶段 0 计划、架构、协议策略、兼容性矩阵、测试记录

## 2. 页面

| 页面 | 当前内容 |
| --- | --- |
| 连接 | G100D 主视觉、连接三步、状态卡、连接向导、厂商省电引导 |
| 传输 | 队列统计、进行中/已完成/失败空状态、传输策略说明 |
| 相册 | 相机/本地切换、搜索、全部/照片/RAW/视频/收藏筛选、空状态 |
| 遥控 | 16:9 取景占位、LIVE/电量/帧率占位、快门/录像/参数禁用态、能力状态 |
| LUT | LUT 库空状态、导入/预览/导出工作流、RAW 策略说明 |
| 设置 | 主题、动态取色、传输策略、厂商省电、诊断、缓存、关于、许可、安全承诺 |
| 关于 | 版本、安全承诺、技术栈、构建信息、非官方声明 |

所有未接入的能力都显示“待验证/计划中/阶段 2”，没有模拟相机数据或伪造进度。

## 3. 构建与测试

| 项 | 命令 | 结果 |
| --- | --- | --- |
| Debug 构建 | `.\gradlew.bat :app:assembleDebug` | 通过 |
| 单元测试 | `.\gradlew.bat :protocol:test :core:testDebugUnitTest` | 通过（6 个测试） |
| Instrumentation 编译 | `.\gradlew.bat :app:assembleDebugAndroidTest` | 通过（未运行） |
| Lint | `.\gradlew.bat :app:lintDebug` | 通过，0 issue |
| APK | `app/build/outputs/apk/debug/app-debug.apk` | 58.22 MB |

APK 信息：

```text
package: com.lumalink.g100d
versionName: 0.1.0-debug
versionCode: 1
minSdk: 29
targetSdk: 35
compileSdk: 35
SHA-256: D6AB86CAD5E73A766B3FB356A96D2634CED618988449C18D3F422588FEF433D4
签名: APK Signature Scheme v2 验证通过
```

## 4. 未完成 / 未验证

- **未安装到真机**：当前没有连接 Android 设备，也没有模拟器；安装、启动、手势导航、深色模式截图需要你在小米 13 Ultra 上验证。
- **未连接相机**：阶段 1 不发起任何相机请求；连接、传输、相册索引、遥控、LUT 均在后续阶段。
- **未做性能基准**：传输速度、缩略图滚动、转码性能尚未测量。
- **APK 偏大**：debug 包含完整 `material-icons-extended`；release 开启 R8 后会显著缩小，阶段 6 处理。
- **CI 未实际跑过**：工作流已写好，本地未推送到 GitHub。

## 5. 已知问题

- 构建时 AGP 会尝试写 `C:\Users\Taoka\.android\analytics.settings`，在沙箱下被拒绝；仅影响 analytics，不影响构建与签名。项目通过 `ANDROID_USER_HOME` 指向工作区。
- 首次构建需要下载依赖；后续增量构建约 20–45 秒。
- `local.properties` 不进入 git/zip，构建脚本会从 `.dev` 读取 SDK 路径。

## 6. 下一步（阶段 2）

1. 实现 Wi-Fi 连接状态机与 `WifiNetworkSpecifier` 绑定（不自动连接相机，需用户点击）。
2. 实现 `LumixHttpClient` + `ReadOnlyGuard` + MockWebServer 模拟相机。
3. 实现浏览、下载、队列、前台服务、校验、取消/重试。
4. 提供诊断模式；在获得你明确同意前，不向真机相机发送请求。
5. 完成后给出模拟测试结果与真机测试步骤。
