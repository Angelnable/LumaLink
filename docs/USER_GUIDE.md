# LumaLink 使用指南

版本：0.7.7-debug
目标：Panasonic LUMIX DC-G100D（其他 LUMIX 机型未验证）

## 1. 安装

1. 安装 `LumaLink-0.7.7-debug.apk`。
2. 首次启动允许通知（传输进度需要前台服务通知）。
3. Android 13+ 如需保存到相册，允许照片/视频权限。

## 2. 连接相机

1. 相机进入 Wi-Fi 功能画面，选择智能手机连接。
2. 手机 Wi-Fi 加入 `G100D-XXXXXX`。
3. 打开 LumaLink → 连接 → 点“我已手动加入相机 Wi-Fi”。
4. 看到“已连接”和 `LUMIX G100D` 即握手成功。

> G100D 连接后不要操作相机菜单，否则 Wi-Fi 会断开。需要切换模式时，用 App 内按钮。

## 3. 浏览与下载相机文件

1. 连接后进入“浏览相机文件”。
2. 如果列表为空，点“切换到播放模式并读取”，确认发送 `playmode`。
3. 等待 1–2 秒，App 会读取 `total_content_number`，并按 `P100****` / `DL100n` / `/DCIM/100_PANA` 模式探测文件。
4. 勾选文件 → “下载选中”。
5. 下载使用前台服务；完成后保存到系统相册/下载目录。

### 文件列表日志

成功：

```text
content-probe pattern=P-50001 firstIndex=68 url=http://192.168.54.1:50001/P1000068.JPG
content-number total=68 pattern=P-50001 found=68
```

失败时看：

```text
content-probe ... HEAD -> HTTP 404/403/...
content-probe ... GET  -> HTTP 404/403/...
content-number total=68 no filename pattern matched
```

## 4. 实时监看、拍照、录像

- **开始监看**：确认后发送 `recmode` + `startstream`，手机 UDP 49152 接收。
- **快门**：监看运行时可用，发送 `capture`。
- **录像**：开始/停止都要确认，发送 `video_recstart` / `video_recstop`。
- 失败不会自动重试；`err_busy` / `err_critical` 时先停止监看、等待 2 秒再试。
- 相机端录像文件完整性需自行检查。

## 4.1 从 SD 卡/USB 导入

如果相机 Wi-Fi 文件接口被固件限制：

1. 用读卡器把相机 SD 卡接到手机（或手机支持 USB OTG）。
2. 相册 → 本地 → “SD/USB 导入”。
3. 在系统文件选择器中选择 `DCIM/100_PANA` 文件夹。
4. App 会导入 JPEG/RAW/MP4，保存到本地相册/下载目录。
5. 只读取你授权的文件夹，不连接相机、不写相机。

## 5. LUT

1. LUT 页 → 导入 `.cube`（1D/3D）。
2. 选择 LUT，导入照片或视频。
3. 照片：预览/导出到相册。
4. 视频：抽帧预览；可“转码导出”到 `Movies/LumaLink/LUT`。
5. HDR/10-bit 视频可能被 Media3 拒绝，会显示错误。

## 6. 诊断

设置 → 诊断日志：

- 复制：导出完整日志文本。
- 清空：清除内存日志。

反馈文件列表问题时，请提供：

1. `getstate` 响应体；
2. `get_content_info` 的 `total_content_number`；
3. `content-probe` / `content-number` 行；
4. `POST ...:60606/Server0/CDS_control` 的状态和响应头（如果还在尝试 UPnP）。

## 7. 已知限制

- G100D 的 `getinfo` 返回 `err_param`，App 用 `getstate` 回退。
- UPnP Browse 返回 403，文件列表改走内容编号/文件名模式。
- 相机文件列表、下载、LUT 尚未完成全部真机验证。
- 不支持删除相机文件、格式化、修改相机设置、固件/寄存器操作。
