# LumaLink 阶段 6 报告：交付状态与验收清单

日期：2026-10-20
版本：1.0.7-debug
状态：**交付物已生成；文件列表/下载/LUT 仍有待真机确认项，不能宣称全部功能完成。**

## 1. 交付物

| 产物 | 路径 | SHA-256 |
| --- | --- | --- |
| APK | `dist/LumaLink-1.0.7-debug.apk` | 以 `dist/SHA256SUMS.txt` 为准 |
| 源码包 | `dist/LumaLink-1.0.7-src.zip` | 以 `dist/SHA256SUMS.txt` 为准 |
| 校验文件 | `dist/SHA256SUMS.txt` |  |

构建环境：JDK 17.0.13、Android SDK 35、Gradle 8.9、AGP 8.7.3。
版本：versionCode 18 / versionName 0.7.7-debug。

## 2. 自动化验证

- JVM 测试 51 个通过：
  - protocol 38：HTTP guard、browse 解析、UPnP 描述、UPnP Browse、控制命令、访问审批与会话、JPEG 帧重组、内容编号枚举
  - core 5
  - transport 2
  - lut 6
- `:app:lintDebug` 0 issue。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 成功。
- Content-number 单元测试验证：`total=68` 时枚举 `P1000001.JPG` … `P1000068.JPG`。

## 3. 真机已验证

用户 2026-10-19/20 在 LUMIX DC-G100D + 小米手机上确认：

- Wi-Fi 连接与 `bindProcessToNetwork`
- `getstate` 握手（`getinfo` 在 G100D 返回 `err_param`）
- 实时监看（UDP 49152）
- 遥控拍照（`capture -> ok`）
- 录像开始/停止命令返回 `ok`

## 4. 仍待验证

- v0.7.7 的 `P100****` / `DL100n` 文件列表实际结果。
- 文件下载、MediaStore 保存、SHA-256。
- 录像文件完整性。
- LUT 照片/视频导出。
- 1GB / 批量传输。
- 其他品牌手机的 Wi-Fi 绑定。

## 5. 安全边界

- 不写固件、寄存器、分区、签名。
- 读接口仅 GET/HEAD（`getstate`、`getinfo`、`get_content_info`、browse、文件）。
- 控制命令是固定 allowlist：`playmode`、`recmode`、`capture`、`video_recstart`、`video_recstop`、`startstream`、`stopstream`。
- 拍照/录像/监看均由用户点击 + 确认触发；失败不自动重试。
- 相机文件只读；删除/格式化/改设置未实现。

## 6. 结论

阶段 1、2、3 的代码与自动化测试、阶段 4 的监看/拍照真机验证、阶段 5 的 LUT 代码与测试均已完成。阶段 3 的相机文件列表在 v0.7.7 改为内容编号/文件名模式探测，仍需用户安装后确认；在此之前不标记“全部完成”。
