# LumaLink 阶段 9：UI/遥控修复与拍摄参数

日期：2026-10-20
版本：2.3.0-debug

## 1. 修复

### 重复 Snackbar

传输队列提示原先在 `showSnackbar` 返回后才清空，切换页面会再次触发。
现在先消费 `message` 再显示，切换页面不会重复弹出“已加入传输队列”。

### 传输历史删除

已完成/失败/已取消记录增加“删除记录”入口：

- 已完成记录：
  - 仅删除记录；
  - 删除记录和本地文件。
- 失败/取消记录：
  - 只删除记录（没有本地文件）。

删除本地文件使用 `localUri`：MediaStore 内容 URI 走 `ContentResolver.delete`，`file://` 走文件删除。

### 遥控首次命令失败

G100D 偶尔会在模式切换后关闭第一条连接。现在仅对**连接级错误**（Offline/Timeout）在 400ms 后重试一次：

- `recmode` / `startstream`
- `capture`
- `video_recstart` / `video_recstop`
- `stopstream` 清理

协议拒绝（`err_*`）不重试。UI 文案已改为“最多重试一次”。

### 按钮尺寸

录像与快门统一为 `large` 尺寸（78dp），图标 32dp。

### 相机相册预览

相机文件行增加“眼睛”按钮：

- 点击后按需读取 `DT...JPG` 缩略图；
- JPEG/RAW 均可用缩略图预览；
- 视频提示下载后播放；
- 预览同样遵循“一次 Browse 一次下载”，会先刷新 access。

### 拍摄参数

遥控页新增：

- ISO：AUTO/100–12800
- 快门：1/4000–60s
- 光圈：f/1.7–f/16
- 白平衡：AUTO/日光/阴天/阴影/白炽灯/闪光灯/自定义1–4
- 曝光补偿：-3.0–+3.0（实验字段）

协议：

```text
mode=setsetting
type=iso|focal|shtrspeed|whitebalance|exposure
```

白平衡值来自公开 Lumix Web Remote 资料；曝光补偿字段没有可靠公开文档，标记为实验，失败会显示相机错误。
参数写入只在用户点选时发生，不做自动批量设置。

## 2. 验证

- `:app:assembleDebug` 成功
- `:app:assembleDebugAndroidTest` 成功
- `:app:lintDebug` 通过
- 非 LUT JVM 测试：protocol 38 + core 5 + transport 2 = 45

## 3. 待真机确认

- 删除记录/本地文件的实际行为（含 RAW 在 Downloads 的删除）。
- 首个命令重试是否消除“第二次才成功”。
- 相机相册缩略图预览速度与清晰度。
- ISO/快门/光圈/白平衡是否按预期生效。
- 曝光补偿字段是否为 G100D 接受的 `exposure`；若返回 `err_param`，需要抓包/菜单 API 再确认。
- 参数调整仅在 M/A/S 模式有意义。

## 4. 产物

| 产物 | 路径 |
| --- | --- |
| APK | `dist/LumaLink-2.3.0-debug.apk` |
| 源码 | `dist/LumaLink-2.3.0-src.zip` |
