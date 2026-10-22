# LumaLink 阶段 8 报告：移除 LUT，聚焦图传与遥控拍摄

日期：2026-10-20
版本：2.1.0-debug

## 1. 变更

用户决定移除 LUT 功能，只保留图传、相册和遥控拍摄。

已从 App 构建中移除：

- `:lut` 模块依赖（`app/build.gradle.kts`）
- `:lut` 模块 include（`settings.gradle.kts`）
- 底部导航 LUT 项
- `Destination.Lut` 路由
- `LutViewModel` / `LutScreen` 路由与 `AppContainer.lutRepository`
- `nav_lut` 字符串
- 关于页 LUT 文案
- 导航冒烟测试中的 LUT 断言

说明：

- `lut/` 源码目录已删除，源码包不再包含 LUT 模块。
- `core` 中的 `LutEntity` / `LutDao` 已删除；数据库升级到 v4，并用 `MIGRATION_3_4` 删除旧 `luts` 表。
- Media3 Transformer 等 LUT 专用依赖不再随 App 打包。

## 2. 保留功能

- 相机 Wi-Fi 连接、`accctrl` 访问
- `/Server0/CDS_control` 目录读取
- JPEG / MP4 / RW2 下载
- 每文件自动刷新 Browse/access
- RAW 内嵌 JPEG 预览与相册 RAW 筛选
- 实时监看、遥控拍照、录像命令
- 相册、收藏、标签、SD/USB 导入
- 大文件空间预检、120s 读超时
- 相机 Wi-Fi 中断时直接取消，不做断点续传/恢复

## 3. 验证

- `:app:assembleDebug` 成功
- `:app:assembleDebugAndroidTest` 成功（导航测试已移除 LUT）
- `:app:lintDebug` 通过
- 非 LUT JVM 测试通过：
  - protocol 38
  - core 5
  - transport 2
  - 合计 45
- APK 大小从 65.17MB 降到约 58.99MB，确认 LUT/Media3 Transformer 未打包
- 2.3GB MP4 传输真机通过，实测约 3.8–4MB/s
- 通过 App 开始/停止录像生成的 MP4 完整性已确认

## 4. 产物

| 产物 | 路径 |
| --- | --- |
| APK | `dist/LumaLink-2.1.0-debug.apk` |
| 源码 | `dist/LumaLink-2.1.0-src.zip` |
| 校验 | `dist/SHA256SUMS.txt` |

## 5. 注意

- 源码包不再包含 `lut/` 目录。
- 相机固件 Ver.1.5 已修复 Wi-Fi 传输性能，实测约 4.2MB/s。
- 10MB/s 仍取决于相机端 Wi-Fi/HTTP 实现。
- RAW-only 不再有 LUT 需求；App 仍支持 RAW 传输和预览。
