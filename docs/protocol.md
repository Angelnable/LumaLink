# 协议与只读策略

## 重要声明

Panasonic 没有公开 G100D 的 HTTP/UDP/BLE API。本文中的端点来自公开社区资料与官方 App 行为推断，**不是官方协议**，必须在真机上以只读方式确认后才能启用。

## 真机发现：文件列表走 UPnP，不走 /cam.cgi?mode=browse

2026-10-19 用户真机连接 G100D 后：

- `GET /cam.cgi?mode=getinfo` → 200
- `GET /cam.cgi?mode=getstate` → 200
- `GET /cam.cgi?mode=browse&dir=/DCIM` → 200，但 0 个文件

公开实现（[pylumix](https://github.com/abichinger/pylumix)）显示 G100D 类机型使用 **UPnP ContentDirectory**：

```text
POST http://192.168.54.1:60606/Server0/CDS_control
SOAPAction: "urn:schemas-upnp-org:service:ContentDirectory:1#Browse"
BrowseFlag: BrowseDirectChildren
ObjectID: 0
pana:X_FromCP: LumixLink2.0

文件下载基址: http://192.168.54.1:50001/
```

v0.7.1 进一步读取 UPnP 设备描述（`description.xml`），解析 `ContentDirectory` 的真实 `controlURL`；若描述不可用才回退到 `:60606/Server0/CDS_control`。同时记录 403 响应头，并对 `get_content_info` 的 `unexpected end of stream` 做一次只读重试。

v0.5.1 已实现只读 UPnP Browse：

- 只发送 `BrowseDirectChildren`，不发送 `capture`、`setsetting`。
- **真实 G100D 连接后手动操作相机菜单会断开 Wi-Fi**，因此相机必须由 App 切到播放模式。v0.6.0 提供一个用户确认按钮，只发送固定命令：
  ```text
  GET /cam.cgi?mode=camcmd&value=playmode
  ```
  等待 1.2 秒后再发 UPnP Browse；不会自动发送，也不提供其他控制命令。
- 旧的 `cam.cgi?mode=browse` 已确认真机返回 `<result>err_param</result>`，只作为回退，不是主路径。
- 解析 SOAP `Result` 中的 DIDL-Lite `<item><dc:title><res>`，`res` 的绝对/相对 URL 都支持。
- 若 UPnP 不可用，回退到旧的 `/cam.cgi?mode=browse` 只读探测。
- 文件下载允许 `:50001` 的 GET，仍由 `ReadOnlyGuard` 限制为本地主机。

## 2026-10-20 真机突破：内容编号文件

真机日志：

```text
GET /cam.cgi?mode=get_content_info -> 200
<result>ok</result>
<current_position>67</current_position>
<total_content_number>68</total_content_number>
<content_number>68</content_number>
```

UPnP 描述端点全部 404，`/Server0/CDS_control` 403，说明 G100D 固件不走 UPnP 文件列表。

公开 LUMIX 控制器代码使用内容编号访问：

```text
http://192.168.54.1:50001/DL100<contentNumber>.jpg
```

v0.7.3 在 `get_content_info` 成功后枚举内容编号，对 `DL100n.jpg` 做只读探测；真机显示相机文件名为 `P100****`（文件夹 `100_PANA`），因此 v0.7.6 先做多模式探测：

```text
http://<camera>:50001/DL100<n>.jpg
http://<camera>:50001/P100<nnnn>.JPG
http://<camera>:50001/DCIM/100_PANA/P100<nnnn>.JPG
http://<camera>/DCIM/100_PANA/P100<nnnn>.JPG
```

先用最新编号找到一个可用模式，再枚举全部内容；HEAD 不可用时用 `GET Range: bytes=0-15`。只有响应成功且大小可用才显示。UPnP 与 `cam.cgi browse` 只保留为其他固件回退。

真机 UPnP 描述（SSDP）为：

```text
LOCATION=http://192.168.54.1:60606/Lumix/Server0/ddd
controlURL=http://192.168.54.1:60606/Server0/CDS_control
```

controlURL 正确，但 Browse 仍返回 403；v0.8.0 发现 SSDP LOCATION 在 `/Lumix/Server0/ddd`，而描述里的 controlURL 是 `/Server0/CDS_control`。有些固件要求 `/Lumix/Server0/CDS_control`，因此优先尝试该前缀；Browse 成功后会返回真实 `P100xxxx` 文件名与 `DO0100xxxx` 下载 URL。

公开 Lumix Tools 的下载 URL 为 `:50001/DO<itemId>.<ext>`，例如 `DO01002279.jpg`；这比按 1..68 猜 `P100xxxx` 更可靠。

## 阶段 4 固定控制命令（用户触发）

| 能力 | 命令 | 说明 |
| --- | --- | --- |
| 播放模式 | `camcmd&value=playmode` | 浏览文件前 |
| 拍摄模式 | `camcmd&value=recmode` | 开始监看/拍照前 |
| 拍照 | `camcmd&value=capture` | 快门按钮 |
| 录像开始 | `camcmd&value=video_recstart` | 二次确认 |
| 录像停止 | `camcmd&value=video_recstop` | 二次确认 |
| 实时流开始 | `startstream&value=49152` | 手机 UDP 49152 |
| 实时流停止 | `stopstream` | 停止监看/断开 |

实时流为 UDP MJPEG；`LiveViewFrameAssembler` 按 JPEG SOI/EOI 重组跨包帧。失败不自动重试。

## 候选端点

| 能力 | 候选 | 状态 |
| --- | --- | --- |
| 状态/型号 | `GET /cam.cgi?mode=getstate`、`getinfo` | 已真机 200 |
| 文件列表 | UPnP `:60606` Browse（主）；`GET /cam.cgi?mode=browse`（回退） | UPnP 待复测 |
| 文件下载 | `http://<相机>:50001/...`（主）；`/DCIM/...`（回退） | 待实测 |
| 拍照 | `GET /cam.cgi?mode=camcmd&value=capture` | 待实测 |
| 录像 | `GET /cam.cgi?mode=camcmd&value=startrec/stoprec` | 待实测 |
| 实时取景 | `GET /cam.cgi?mode=startstream&value=<port>` + UDP JPEG | 待抓包 |
| 缩略图 | `GET /cam.cgi?mode=thumb...` | 待实测 |
| BLE | 标准 GAP 扫描/配对；私有 GATT | 不写；仅扫描/配对引导 |

直连时相机常见地址为 `192.168.54.1`，但不同连接模式（相机热点、路由器、手机热点）会变化；实现必须允许手动 IP 与网关探测。

## 只读策略

阶段 2 的诊断/探测只允许：

- `GET` / `HEAD`；
- 白名单：`getstate`、`getinfo`、`browse`、文件 GET、`startstream`（若用户单独同意）；
- 禁止：`setsetting`、`camcmd capture`、`startrec`、`stoprec`、任何 POST/PUT/DELETE；
- 5 秒超时；失败即断开；不循环重连；
- 日志脱敏：SSID、密码、IP、文件名可配置隐藏。

## 控制策略

拍照/录像属于相机写操作，只能在用户明确点击后发送：

- 录像开始/停止二次确认；
- 不自动重试控制命令；
- 断线时提示用户检查相机，不自动补发；
- 参数修改在 v1 不实现；仅只读展示。

## 完整性

相机没有公开的文件哈希接口。传输校验只能做到：

1. 响应 `Content-Length` 与实际字节数一致；
2. 文件结构校验（JPEG SOI/EOI、MP4 box、RW2 TIFF 头）；
3. 本地 SHA-256 记录，用于重复检测与后续校验。

不会声称“与相机哈希一致”。
