# 阶段 2 报告：连接、只读协议与传输队列

日期：2026-10-19
版本：0.2.0-debug
状态：代码与自动化测试完成；**真实 G100D 连接与传输待用户授权后测试**。

## 1. 本阶段改动范围

- `:protocol`
  - `LumixHttpClient`：OkHttp、5 秒连接超时、30 秒读超时、不自动重试。
  - `ReadOnlyGuard`：只允许 GET/HEAD、只允许 `getstate/getinfo/browse` 和 `/DCIM/`、`/PRIVATE/` 文件路径。
  - `HostGuard`：只允许私有地址/链路本地地址/loopback，拒绝公网主机。
  - `LumixHttpProtocol`：握手、状态、目录浏览、文件流打开、Range 请求。
  - `LumixBrowseParser` / `LumixStatusParser` / `LumixInfoParser`：容错 XML 解析，支持属性/子元素/文件名回退。
- `:core`
  - Room 数据库 `LumaLinkDatabase`（version 1，schema 已导出）。
  - `TransferEntity`、`TransferDao`、`TransferRepository`。
  - 传输队列持久化，App 重启后仍可恢复。
- `:transport`
  - `ConnectionManager`：`WifiNetworkSpecifier` + `requestNetwork` + `bindProcessToNetwork`，60 秒连接超时、20 秒握手超时、断线解绑。
  - `WifiScanner`：扫描附近 Wi-Fi，优先标记 LUMIX/G100/DC-G100。
  - `TransferService`：前台服务（`dataSync`）、通知进度、取消/重试、Wi-FiLock + Partial WakeLock。
  - `TransferDownloader`：`.part` 暂存、HTTP Range 断点续传、大小/结构校验、SHA-256、MediaStore 保存。
  - 连接页：SSID/密码/相机地址、扫描、权限请求、连接状态、错误提示。
  - 相机文件页：目录导航、多选、加入下载队列。
  - 传输页：队列统计、进度/速度/ETA、取消/重试、完成后清理。
- `:app`
  - `LumaLinkApplication` 实现 `TransferServiceHost`，共享 Repository 与相机会话。
  - `AppContainer` 注入数据库、连接管理器、Wi-Fi 扫描器。
  - `ConnectViewModel` / `TransfersViewModel` 构造器注入工厂。
- 权限：`NEARBY_WIFI_DEVICES`、`ACCESS_FINE/COARSE_LOCATION`（≤32）、`POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`WAKE_LOCK`。

## 2. 安全边界

- 阶段 2 **没有连接过真实相机**，也没有发送任何真实请求。
- 只读闸门在发出请求前检查方法和 URL；`camcmd`、`setsetting`、`startstream`、POST/PUT 在 v0.2 中不可达。
- 公网地址被 `HostGuard` 拒绝，避免相机返回内容把 App 变成通用 HTTP 客户端。
- 传输只由用户在相机文件页勾选并点击“下载选中”触发。
- 不删除相机文件；不修改相机设置；不自动拍照/录像。

## 3. 构建与测试

| 项 | 命令 | 结果 |
| --- | --- | --- |
| Debug 构建 | `.\gradlew.bat :app:assembleDebug` | 通过 |
| 协议单测 | `:protocol:test` | 19 个通过 |
| Core 单测 | `:core:testDebugUnitTest` | 3 个通过 |
| Transport 单测 | `:transport:testDebugUnitTest` | 2 个通过 |
| Lint | `:app:lintDebug` | 通过，0 issue |
| APK | `app/build/outputs/apk/debug/app-debug.apk` | 58.52 MB |

APK：

```text
package: com.lumalink.g100d
versionName: 0.2.0-debug
versionCode: 2
minSdk 29 / targetSdk 35 / compileSdk 35
foregroundServiceType=0x00000001 (dataSync)
usesCleartextTraffic=true（仅由 HostGuard 限制到本地地址）
```

自动化测试覆盖：端点构造、只读/主机拦截、browse/status/info 解析、MockWebServer 握手/状态/浏览/文件流、格式化、传输进度与 ETA。

## 4. 真机结果（2026-10-19）

用户已成功连接 G100D（v0.3.1）：

```text
使用当前 Wi-Fi：if=wlan0 addrs=192.168.54.10
bindProcessToNetwork=true
GET /cam.cgi?mode=getinfo -> 200
GET /cam.cgi?mode=getstate -> 200
握手成功：LUMIX G100D / 固件 "1.0"
```

结论：`CHANGE_NETWORK_STATE` 修复有效，握手/只读状态读取通过。相机以 `192.168.54.1` 提供服务，手机地址 `192.168.54.10`。

## 4.1 目录浏览修正

- `/DCIM` 返回 200 但 App 显示 0 个文件。
- 公开 pylumix 实现显示文件列表走 **UPnP ContentDirectory**：`POST http://192.168.54.1:60606/Server0/CDS_control`，文件下载基址 `http://192.168.54.1:50001/`。
- v0.5.1 实现只读 UPnP Browse + DIDL-Lite 解析；不发送 `playmode`，要求用户手动把相机切到播放模式。
- 旧的 `/cam.cgi?mode=browse` 保留为回退。
- `getinfo/getstate/browse` 的真实返回格式；当前解析器为容错实现，需要真实响应校准。
- 文件下载 URL、Range 支持、缩略图接口（阶段 2 未用缩略图）。
- MediaStore 保存路径与 RAW 文件 MIME 行为。
- 前台服务在 HyperOS 后台存活、通知权限被拒时的表现。
- 传输速度与 2.4GHz Wi-Fi 实际吞吐。

## 5. 真机只读测试申请（需要你明确同意）

- **目的**：确认 G100D 是否响应 `getinfo/getstate/browse`，校准解析器；只下载 1 张最小 JPEG 验证文件读取。
- **步骤**：相机进入 Wi-Fi 直连模式；手机安装 v0.2.0；在 App 内输入相机 SSID/密码；点击“连接相机”；连接后打开“浏览相机文件”。
- **只读保证**：仅 GET；不发送 `camcmd`/`setsetting`/`startstream`；不拍照、不录像、不改设置、不删除文件。
- **风险**：相机 HTTP 服务可能卡顿；Wi-Fi 可能断开；不影响固件/设置，关闭相机 Wi-Fi 或重启即可恢复。
- **异常处理**：App 20 秒握手超时即断开并解绑；不循环重连。
- **需要你回复**：`同意阶段2只读连接测试`，并告知相机固件版本与手机型号/系统版本。

## 6. 下一步

- 若只读测试通过：进入阶段 3 相册索引/缩略图/分类/搜索。
- 若协议不匹配：根据 App 错误信息与（如你同意）抓包结果调整解析器；仍不发送写请求。
- 阶段 3 前会把测试日志与真实响应样本（脱敏）写入 `docs/protocol.md`。
