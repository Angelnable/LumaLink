# 阶段 0：开发计划、风险与确认事项

日期：2026-10-19
状态：已获用户批准；开发环境安装到 `D:\LumaLink\.dev`。

## 结论

1. 项目可做，但 G100D 的 HTTP/UDP/BLE 协议未公开，是最大不确定性。协议层必须做运行时能力探测，未验证能力明确降级。
2. 已确认 Google Maven、Maven Central、Gradle 分发、Microsoft JDK 17 可下载；便携工具链已安装。
3. 真机连接与抓包需要用户配合；没有真机数据的功能标注“未实测”。

## 安全边界

- 不刷写固件、不写寄存器、不改系统分区、不绕过签名。
- 未获明确授权前不连接相机。
- 默认只读：GET/HEAD + allowlist；无 `setsetting`；控制必须用户触发。
- 不删除相机文件；v1 只清理本地文件。
- 超时/协议不匹配立即断开；不重试写请求。

## 只读探测确认书

阶段 2 若需要连接相机，必须先由用户确认：

- **目的**：确认 G100D 的 HTTP 端点/返回格式、列表/缩略图/下载/Range、实时取景端口与帧头、BLE 广播；不写设置。
- **步骤**：用户手动将相机置于 Wi-Fi 直连；App 仅发送 allowlist 内的 GET/HEAD；下载 1 张最小 JPEG；导出脱敏日志。
- **风险**：相机 HTTP 服务可能无响应/界面卡顿；Wi-Fi 发热/耗电；未知请求可能让相机断开 Wi-Fi；不涉及固件/设置。
- **只读保证**：代码级 allowlist；无 POST/PUT、无 `setsetting`、无 `capture/startrec`（除非另行同意）。
- **异常断开**：5 秒超时即断开、解绑网络、停服务；不循环重连。
- **回滚**：退出 App/关闭相机 Wi-Fi 即恢复；卸载 App；相机无持久变更。

## 技术选型

Kotlin 2.0.21 + Jetpack Compose + Material 3；AGP 8.7.3 / Gradle 8.9 / JDK 17；compileSdk/targetSdk 35，minSdk 29；Room 2.6.1 + DataStore；OkHttp 4.12；Media3 1.5.1；前台服务 + WorkManager；JUnit4 + MockWebServer + Compose UI Test。

DI 采用构造器注入 + `AppContainer`，避免 Hilt/KSP 版本耦合。v1 不引入 FFmpeg/libraw，控制 APK 体积与 GPL/LGPL 风险。

## 协议候选端点

松下没有公开 API。以下端点来自公开社区资料，**全部待 G100D 实测**：

| 能力 | 候选 | 状态 |
| --- | --- | --- |
| 状态/型号 | `/cam.cgi?mode=getstate` / `getinfo` | 社区资料；字段未知 |
| 文件列表 | `/cam.cgi?mode=browse&dir=...` | 社区资料；XML 未知 |
| 文件下载 | `/DCIM/...` | 官方行为推断 |
| 拍照 | `/cam.cgi?mode=camcmd&value=capture` | 部分机型社区验证 |
| 录像 | `/cam.cgi?mode=camcmd&value=startrec/stoprec` | 待验证 |
| 实时取景 | `/cam.cgi?mode=startstream&value=<port>` + UDP JPEG | 待抓包 |
| 缩略图 | `/cam.cgi?mode=thumb...` | 待验证 |
| BLE | 标准 GAP 扫描/配对；私有 GATT 未知 | 官方 App 支持；不写 |

## 阶段计划

| 阶段 | 交付 | 验收 |
| --- | --- | --- |
| 1 | 工程/UI 骨架、导航、设置 | `assembleDebug` 成功、单测通过、APK 可安装 |
| 2 | 连接、传输队列、前台服务、校验 | MockWebServer 集成测试；真机待测 |
| 3 | 相册索引、分类、搜索、收藏、批量 | 1000 条模拟数据；DAO/过滤器测试 |
| 4 | 实时监看、拍照/录像、降级 | 合成帧 30fps；控制状态机测试 |
| 5 | LUT 导入/照片导出/视频转码/RAW 策略 | 33³ cube；测试图色差；10 秒视频转码 |
| 6 | 兼容性矩阵、性能基准、APK/zip、文档 | 构建产物与已知限制清单 |

## 主要风险

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| G100D 协议未公开 | 高 | 能力探测、容错解析、只读探测、明确降级 |
| 相机 HTTP 服务脆弱/单连接 | 高 | 单飞、5s 超时、熔断、不重试写 |
| Wi-Fi 4 2.4GHz 吞吐低 | 中高 | 基准测试、并发可调、不承诺虚假倍数 |
| 厂商后台/Wi-Fi 绑定限制 | 高 | 前台服务、WakeLock、省电白名单引导、真机矩阵 |
| 实时取景协议未知 | 高 | SOI/EOI 流重组、接口隔离、无协议则降级 |
| BLE 私有 GATT 未知 | 高 | v1 不写；仅标准扫描/配对引导 |
| RAW 无法直接显影 | 高 | 内嵌预览 + 明确标注；libraw 另评估 |
| 20MP LUT/4K 转码 OOM/发热 | 中高 | 分块 GPU、硬件编码、热提示 |
| 大文件/存储兼容 | 中 | 64 位、流式、Downloads/SAF、空间预检 |
| 无真机验证 | 高 | 模拟服务器 + 用户真机回传；标注未实测 |
| 法律/商标/逆向合规 | 中 | 仅互操作、不复制逆向代码、不使用松下素材 |
