# 阶段 5 测试记录

日期：2026-10-19
版本：1.0.7-debug (versionCode 37)

## 构建

| 项 | 命令 | 结果 |
| --- | --- | --- |
| Debug 构建 | `.\gradlew.bat :app:assembleDebug` | 通过 |
| Instrumentation 编译 | `.\gradlew.bat :app:assembleDebugAndroidTest` | 通过 |
| Lint | `.\gradlew.bat :app:lintDebug` | 通过，0 issue |
| LUT 单测 | `.\gradlew.bat :lut:testDebugUnitTest` | 5/5 通过 |

APK：约 65 MB（Media3 引入后增大），versionCode 14 / versionName 0.7.3-debug。

## 单元测试

| 测试 | 数量 |
| --- | --- |
| protocol | 38（含内容编号 2、UPnP 6、控制 5、帧重组 3） |
| core | 5 |
| transport | 2 |
| lut | 6（含 LUT cube 映射） |
| 总计 | 51 |

`CubeLutParserTest` 覆盖：3D identity、3D invert、1D + DOMAIN、错误数据量、缺少 size。

## 未执行/未验证

- 真实 `.cube` 文件导入与列表管理。
- 真实 20MP 照片预览/导出、RAW 内嵌预览。
- Photo Picker / SAF 在 Xiaomi HyperOS 上的行为。
- 视频抽帧与旋转。
- Media3 视频转码（代码已接入，未真机验证；HDR/10-bit 可能被拒绝）。
- 相机连接/传输（等待 v0.3.1 修复后的真机测试）。

## 说明

- 视频“转码导出”只显示明确提示，不伪造文件。
- RAW 导出的是内嵌预览 JPG，界面明确标注“非 RAW 显影”。
