# 阶段 3 报告：相册索引、分类与诊断

日期：2026-10-19
版本：0.4.1-debug
状态：相机连接已由用户真机验证成功；目录浏览解析仍在按真实响应校准。

## 0.7.6 多文件名模式

相机屏幕显示文件夹 `100_PANA`、文件名 `P100****`。v0.7.6 增加：

- `DL100<n>.jpg`
- `P100<nnnn>.JPG`（端口 50001）
- `/DCIM/100_PANA/P100<nnnn>.JPG`（端口 50001）
- `/DCIM/100_PANA/P100<nnnn>.JPG`（端口 80）
- HEAD 不可用时用 `GET Range: bytes=0-15`

先用最新编号发现可用模式，再枚举全部内容。UPnP 描述已通过 SSDP 找到正确 controlURL，但 Browse 403 仍在诊断。

## 0.7.3 内容编号文件列表

真机 `get_content_info` 成功，`total_content_number=68`；UPnP 描述 404、control 403，确认 G100D 不走 UPnP。

公开 LUMIX 控制器实现使用 `http://<camera>:50001/DL100<contentNumber>.jpg`。v0.7.3 枚举内容编号并用只读 HEAD 探测，成功才显示；这是主路径。

## 0.6.0 播放模式切换

用户反馈：G100D 连接后，只要操作相机菜单就会断开 Wi-Fi，因此不能在相机上手动切播放模式。真机 `/cam.cgi?mode=browse` 返回 `err_param`，确认该端点无效。

v0.6.0：

- 新增“切换到播放模式并读取”按钮 + 二次确认。
- 只发送固定命令 `GET /cam.cgi?mode=camcmd&value=playmode`，等待 1.2 秒，再走 UPnP Browse。
- 不自动发送，不开放拍照/录像/改设置。
- 仍保留 `/cam.cgi?mode=browse` 回退，并把 `err_param` 作为错误记录。

## 0.5.1 相机目录修复（UPnP）

公开 pylumix 实现显示 G100D 类的文件列表使用 UPnP ContentDirectory：

```text
POST http://192.168.54.1:60606/Server0/CDS_control
BrowseDirectChildren, ObjectID=0
文件: http://192.168.54.1:50001/...
```

v0.5.1 实现只读 Browse + DIDL-Lite 解析；不发送 `playmode`，需要用户手动把相机切到播放模式；旧 `/cam.cgi?mode=browse` 作为回退。

## 0.4.1 相机目录修复

用户真机日志：

```text
GET .../cam.cgi?mode=getinfo -> 200
GET .../cam.cgi?mode=getstate -> 200
握手成功：LUMIX G100D / 固件 "1.0"
GET .../cam.cgi?mode=browse&dir=/DCIM -> 200
```

但 `/DCIM` 显示 0 个文件。修复：

- `LumixBrowseParser` 现在把 `<dir>` / `<directory>` / `<folder>` 标签本身当作文件夹，即使没有 `type="folder"` 属性。
- `browse` 对目录参数做只读回退：`/DCIM` → `DCIM`；`/DCIM/100_PANA` → `DCIM/100_PANA` → `100_PANA`。
- 诊断日志新增 browse 响应体（截断 1500 字符），用于确认真实 XML/JSON 格式。
- 版本 0.4.1（versionCode 6）。

## 0. 0.3.1 连接失败修复

用户真机测试 v0.2.0 时连接失败，截图错误为：

```text
缺少 Wi-Fi 权限：com.lumalink.g100d was not granted either of these permissions:
android.permission.CHANGE_NETWORK_STATE, android.permission.WRITE_SETTINGS.
```

根因：`ConnectivityManager.requestNetwork` 需要 `CHANGE_NETWORK_STATE`，但 manifest 未声明，导致在系统 Wi-Fi 确认弹窗之前就抛出 `SecurityException`。

v0.3.1 修复：

- 增加 `android.permission.CHANGE_NETWORK_STATE`（普通权限，无运行时弹窗）。
- `NetworkRequest` 不再移除 `NET_CAPABILITY_INTERNET`，改用标准 Wi-Fi 请求。
- 新增“我已手动加入相机 Wi-Fi”备用路径：在系统设置手动加入相机热点后，App 绑定当前 Wi-Fi 并直接握手。
- 新增网络接口/地址/DNS 诊断日志，便于定位绑定或路由问题。
- 版本号 0.3.1（versionCode 4）。

## 1. 本阶段改动范围

- **本地相册**
  - 从 MediaStore 读取 `Pictures/LumaLink`、`Movies/LumaLink`、`Download/LumaLink/RAW`。
  - 网格缩略图：图片/视频用 Coil；RAW 显示类型占位，不做假预览。
  - 视频缩略图使用 `coil-video` 抽帧。
  - 分类：全部 / 照片 / RAW / 视频 / 收藏。
  - 搜索：文件名 + 标签。
  - 选择模式、批量删除本地文件（二次确认，不删除相机文件）。
  - 详情页：预览、大小/类型、收藏、标签、分享、删除本地。
- **相机相册**
  - 连接 G100D 后浏览 `/DCIM` 目录，支持文件夹进入/返回/刷新。
  - 多选文件加入传输队列。
  - 缩略图明确标注“待协议验证”，当前只显示文件信息，不伪造缩略图。
- **Room v2**
  - 新增 `media_overlays` 表（uri / favorite / tags / updatedAt）。
  - `MIGRATION_1_2` 保留已有传输队列数据。
  - 数据库 schema 已导出到 `core/schemas`。
- **诊断**
  - `Diagnostics` 内存环形日志（最多 250 条）。
  - 记录 Wi-Fi 连接状态、只读 HTTP 请求（方法/URL/状态码/耗时/错误）、传输开始/完成/失败。
  - 设置 → 诊断日志：查看、复制、清空。
- **权限**
  - `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`（Android 13+）。
  - `READ_EXTERNAL_STORAGE`（≤ Android 12）。

## 2. 安全边界

- 相册只删除本地 MediaStore 文件；相机文件不可删除。
- 相机侧仍然只读：`getinfo/getstate/browse` + 文件 GET。
- 相机缩略图没有伪造；在协议确认前显示文件信息占位。
- 没有连接真实相机；诊断日志不会上传。

## 3. 构建与测试

| 项 | 命令 | 结果 |
| --- | --- | --- |
| Debug 构建 | `.\gradlew.bat :app:assembleDebug` | 通过 |
| Instrumentation 编译 | `:\app:assembleDebugAndroidTest` | 通过 |
| 协议单测 | `:protocol:test` | 19/19 |
| Core 单测 | `:core:testDebugUnitTest` | 5/5（含诊断 2） |
| Transport 单测 | `:transport:testDebugUnitTest` | 2/2 |
| Lint | `:app:lintDebug` | 通过，0 issue |

总计 26 个 JVM 测试通过。

APK：约 58.8 MB（debug + 完整图标集），versionCode 4 / versionName 0.3.1-debug。

## 4. 未验证项

- 真实 G100D 连接、browse 返回格式、文件下载（阶段 2 待授权测试）。
- 真实传输后的 MediaStore 索引、缩略图、视频抽帧、删除权限。
- 相机缩略图端点（阶段 4/协议确认后再做）。
- Android 14 部分照片授权（`READ_MEDIA_VISUAL_USER_SELECTED`）行为。
- 大图/4K 视频网格滚动性能。

## 5. 下一步

- 用户安装 v0.3.0 后，先验证本地相册（可手动放入测试图片/视频到 `Pictures/LumaLink`）。
- 授权后做阶段 2 只读相机测试；成功后阶段 4 实时监看/拍照/录像。
- 若相机缩略图协议确认，再替换相机网格占位。
