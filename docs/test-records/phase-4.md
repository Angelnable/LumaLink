# 阶段 4 测试记录

日期：2026-10-19
版本：1.0.7-debug (versionCode 37)
设备：未连接真实相机（等待用户授权）

## 自动化测试

| 模块 | 数量 | 结果 |
| --- | --- | --- |
| protocol | 33 | 通过 |
| core | 5 | 通过 |
| transport | 2 | 通过 |
| lut | 6 | 通过 |
| 总计 | 46 | 通过 |

新增：

- `LiveViewFrameAssemblerTest`：3 个测试，覆盖跨 UDP 包 JPEG 重组、单包多帧、reset。
- `LumixControlClientTest`：4 个测试，覆盖 playmode、capture、startstream 和 `err_param`。

## 构建

- `:app:assembleDebug` 成功。
- `:app:lintDebug` 0 issue。
- APK：约 65 MB（Media3 依赖）。

## 真机验证（2026-10-20）

- 实时监看：**用户确认可用**（UDP 49152 收到画面）。
- 拍照：**用户确认可用**（`capture -> ok`）。
- 录像开始/停止：命令返回 `ok`；录像文件完整性尚未核对。
- 失败/超时清理：`err_busy` / `err_critical` 出现在重复启动/停止时；需先停止并等待，未做自动重试。

## 说明

所有控制命令都必须由用户在 App 内点击并确认；没有自动拍照、自动录像或后台控制。未验证项不会标记为通过。
