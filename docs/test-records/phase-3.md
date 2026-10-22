# 阶段 3 测试记录

日期：2026-10-19
版本：0.3.1-debug (versionCode 4)

## 构建

| 项 | 命令 | 结果 |
| --- | --- | --- |
| Debug 构建 | `.\gradlew.bat :app:assembleDebug` | 通过 |
| Instrumentation 编译 | `.\gradlew.bat :app:assembleDebugAndroidTest` | 通过 |
| Lint | `.\gradlew.bat :app:lintDebug` | 通过，0 issue |

APK：`app/build/outputs/apk/debug/app-debug.apk`，58.77 MB，versionCode 4 / versionName 0.3.1-debug。

## 0.3.1 修复记录

用户 v0.2.0 真机连接报错：

```text
缺少 Wi-Fi 权限：... was not granted either of these permissions:
android.permission.CHANGE_NETWORK_STATE, android.permission.WRITE_SETTINGS.
```

修复：manifest 增加 `CHANGE_NETWORK_STATE`；标准 Wi-Fi 请求；新增“我已手动加入相机 Wi-Fi”备用路径；增加网络接口/地址/DNS 诊断日志。修复后需要用户重新安装 v0.3.1 验证。

## 单元测试

| 模块 | 数量 | 内容 |
| --- | --- | --- |
| protocol | 19 | 端点、只读闸门、解析器、MockWebServer |
| core | 5 | 格式化 3 + 诊断日志 2 |
| transport | 2 | 进度/ETA |

总计：26 个测试通过。

## 覆盖的功能路径

- MediaStore 查询：仅 LumaLink 目录；无文件时显示空状态。
- 收藏/标签：Room overlay 读写；搜索包含标签。
- 批量删除：二次确认；只删本地。
- 相机目录：连接后浏览/进入/返回；多选入队。
- 诊断：HTTP 日志、连接状态、传输事件；复制/清空。

## 未执行/未验证

- 真实手机上的本地媒体权限、缩略图、视频抽帧、删除。
- 真实 G100D 的相机文件浏览与缩略图（未授权连接）。
- Android 14 部分照片权限。
- 大相册性能。

## 说明

- 相机缩略图未实现，界面明确显示“缩略图待协议验证”，不使用假图。
- 数据库从 v1 升级到 v2 使用 `MIGRATION_1_2`，保留传输队列。
