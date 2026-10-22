# LumaLink 阶段 4 报告：实时监看与遥控

日期：2026-10-19
版本：0.7.6-debug
状态：**实时监看与拍照已真机验证**（用户 2026-10-20）；录像开始/停止命令返回 `ok`，录像文件结果尚未逐项验证。

## 1. 实现范围

### 实时监看

- 用户点击“开始监看”并二次确认后，App 依次发送固定命令：
  ```text
  GET /cam.cgi?mode=camcmd&value=recmode
  GET /cam.cgi?mode=startstream&value=49152
  ```
- 手机绑定 UDP `49152`，接收相机推送的 MJPEG 数据。
- `LiveViewFrameAssembler` 按 JPEG `FF D8 FF` / `FF D9` 标记重组跨 UDP 包的完整帧。
- `BitmapFactory` 解码后显示，统计 fps。
- 停止时发送 `mode=stopstream` 并关闭 socket；不自动重试。
- 连接断开时立即停止接收并清理 socket。

### 拍照

- 快门按钮只在监看运行时可用（确保相机已在拍摄模式）。
- 发送固定命令：
  ```text
  GET /cam.cgi?mode=camcmd&value=capture
  ```
- 发送后显示“已发送拍照命令”；不自动连拍、不自动下载。

### 录像

- 开始/停止都需要二次确认：
  ```text
  GET /cam.cgi?mode=camcmd&value=video_recstart
  GET /cam.cgi?mode=camcmd&value=video_recstop
  ```
- 失败不自动重试；界面保留真实错误信息。
- 不在后台自动开始或停止录像。

## 2. 安全边界

- 控制命令使用 `LumixControlClient` 固定方法，没有通用命令接口。
- 仅允许：`playmode`、`recmode`、`capture`、`video_recstart`、`video_recstop`、`startstream`、`stopstream`。
- 不包含曝光/ISO/白平衡等设置写入，不包含固件、寄存器、分区操作。
- 读文件仍由 `ReadOnlyGuard` 限制为 GET/HEAD、本地相机、`/DCIM`、`/PRIVATE` 或 `:50001`。
- 所有拍照/录像/监看均由用户点击 + 确认触发；失败不写重试循环。

## 3. 测试

| 测试 | 数量 | 覆盖 |
| --- | --- | --- |
| `LiveViewFrameAssemblerTest` | 3 | 跨包 JPEG 重组、多帧、reset |
| `LumixControlClientTest` | 4 | playmode/capture/startstream/错误响应 |

JVM 总计 46 个测试通过（protocol 33 + core 5 + transport 2 + lut 6）；lint 0 issue。

## 4. 真机结果（2026-10-20）

用户日志：

```text
recmode        -> ok
startstream    -> ok
capture        -> ok
video_recstart -> ok
video_recstop  -> ok
```

用户确认：**实时监看画面可显示，遥控拍照可用**；录像开始/停止命令被相机接受，但录像文件结果尚未逐项核对。

仍需注意：上一次流未完全停止时，`startstream` 可能返回 `err_busy`，`stopstream` 可能返回 `err_critical`；应先停止监看、等待 2 秒再重试，不能连续写入。

## 4.1 未验证项

- 录像文件是否完整写入 SD 卡。
- 监看过程中 Wi-Fi 断开后的清理行为。
- 不同分辨率的 UDP 帧分包边界。

以上必须在用户明确同意后逐项验证；不把“命令返回 ok”写成“所有遥控能力已通过”。

## 5. 已知限制

- 实时流只显示，不录制到手机；相机端录像由 `video_recstart/stop` 控制。
- 不处理 HDR/10-bit 实时流。
- 控制命令名来自公开社区实现，G100D 固件可能返回 `err_param`；界面会显示错误而不是伪造成功。
