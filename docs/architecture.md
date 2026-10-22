# 架构说明

## 模块

```text
:app        Application / MainActivity / 导航 / 设置与关于
:core       领域模型、SettingsRepository、通用格式化
:protocol   LumixProtocol 接口、候选端点、结果类型（纯 Kotlin）
:transport  Wi-Fi 绑定、HTTP 传输、UDP 取景、前台服务、连接/传输 UI
:gallery    相机/本地媒体索引与相册 UI
:remote     实时监看与遥控 UI
:lut        LUT 解析、渲染、导出与 UI
:ui         Material 3 主题与通用组件
```

依赖方向：

```text
app ──► gallery / remote / lut / transport
          │        │        │
          └────────┴────────┴──► core / ui / protocol
transport ──► core / ui / protocol
```

Feature 模块之间不互相依赖；共享模型放在 `:core`。`protocol` 不依赖 Android，便于 JVM 单元测试。

## 状态与数据流

- `MainViewModel` 订阅 `SettingsRepository`，向 `MainActivity` 暴露 `StateFlow<AppSettings>`。
- 主题模式与动态取色写入 DataStore；进程重启后恢复。
- 阶段 2 起：连接状态由 `ConnectionRepository` 暴露；传输队列持久化到 Room，前台服务消费队列。
- UI 只渲染已确认的能力；未验证能力显示“待验证/计划中”。

## 导航

- 单 Activity + Navigation Compose。
- 底部 5 项：连接、传输、相册、遥控、LUT。
- 设置/关于为二级页面，顶栏返回。
- Edge-to-edge：`enableEdgeToEdge()`；Scaffold 的 `contentWindowInsets` 置零，由 TopAppBar/NavigationBar 自己处理系统栏；二级页补 `navigationBarsPadding()`。

## 构建约束

- 不依赖 Android Studio；命令行 Gradle 可构建。
- 版本目录固定，避免浮动版本导致不可复现。
- `org.gradle.daemon=false`、`kotlin.compiler.execution.strategy=in-process`，适配受限的构建沙箱。
- `GRADLE_USER_HOME` 与 `ANDROID_USER_HOME` 可指向工作区内，避免写入用户目录。

## 后续设计要点

- `LumixProtocol` 接口隔离 HTTP/UDP/BLE；实现只暴露已探测能力。
- `ReadOnlyGuard` 在 HTTP 客户端层拦截非 allowlist 命令。
- 传输：流式写入 + `.part` + Range 探测；Content-Length/结构/SHA-256 校验。
- 取景：UDP 数据报重组 + JPEG SOI/EOI 扫描，不依赖未确认的帧头。
- LUT：GLES3 3D 纹理 + 分块处理 20MP；视频用 Media3 自定义 GL 效果。
- RAW：提取 RW2 内嵌 JPEG 预览，明确标注“非 RAW 显影”。
