# LumaLink 安全边界

## 绝对红线

以下操作**永不实现**：

- 写固件、刷机、改寄存器、写分区、绕过签名
- 修改相机内部文件系统、格式化 SD 卡、删除相机文件
- 自动拍照、自动录像、后台静默控制相机
- 把相机文件上传到任何服务器

## 只读通道

以下请求只允许 GET/HEAD，且限制在本地相机：

- `/cam.cgi?mode=getstate`
- `/cam.cgi?mode=getinfo`
- `/cam.cgi?mode=get_content_info`
- `/cam.cgi?mode=browse`（回退）
- `/DCIM/...`、`/PRIVATE/...`
- `:50001` 媒体文件

`ReadOnlyGuard` 会在建立 socket 前拒绝非 GET/HEAD、非本地主机、非白名单路径/模式。

## 控制通道

`LumixControlClient` 只包含固定方法，没有通用命令接口：

| 方法 | 固定请求 |
| --- | --- |
| 播放模式 | `camcmd&value=playmode` |
| 拍摄模式 | `camcmd&value=recmode` |
| 拍照 | `camcmd&value=capture` |
| 录像开始 | `camcmd&value=video_recstart` |
| 录像停止 | `camcmd&value=video_recstop` |
| 实时流开始 | `startstream&value=49152` |
| 实时流停止 | `stopstream` |

约束：

- 全部由用户点击 + 确认触发；
- 失败不自动重试；
- 不包含曝光/ISO/白平衡等设置写入；
- 不包含删除/格式化/固件操作。

## 数据与隐私

- 诊断日志仅保存在内存，用户可复制/清空；不上传。
- 下载文件保存到系统 MediaStore；不上传。
- 无账号、无云同步、无分析 SDK。

## 异常处理

- 连接/握手超时：断开并提示。
- UPnP/文件请求失败：显示错误，不伪造文件。
- 控制命令失败：显示相机返回，不重试写入。
- 监看断开：停止 socket 并清理。
