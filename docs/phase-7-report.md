# LumaLink 阶段 7 报告：大文件、LUT 与最终回归清单

日期：2026-10-20
版本：1.4.0-debug

## 1. 本阶段完成

### 大文件传输加固

- 下载前检查 `cacheDir.usableSpace`：
  - 需要 `剩余字节 + 64MB`；
  - 不足时提前失败并显示所需/可用 MB，不把相机拖入超时。
- 下载重试从 2 次提高到 3 次。
- 50001 下载读超时从 60 秒提高到 120 秒。
- 每个文件下载前仍自动刷新 Browse/access（v1.2.0 起）。

### 已验证的传输链路

- JPEG：`Pictures/LumaLink`
- MP4：`Movies/LumaLink`
- RW2：`Download/LumaLink/RAW`
- RW2 内嵌 JPEG 预览：App 相册可显示
- 相机目录：`/Server0/CDS_control` 200，66 条
- 每文件刷新访问解决 50001 `Connection reset`

## 2. LUT 真机回归清单（待用户执行）

准备：

- 一个 `.cube`（1D 或 3D）
- 一张 JPEG
- 一张 RW2
- 一段 10 秒 MP4

步骤：

1. LUT → 导入 `.cube`；
2. 选择 JPEG，确认原图/预览切换；
3. 导出照片，检查 `Pictures/LumaLink/LUT`；
4. 选择 RW2，确认使用内嵌 JPEG 预览，且提示“非 RAW 显影”；
5. 选择 10 秒 MP4，导出，检查：
   - 输出在 `Movies/LumaLink/LUT`；
   - 播放正常；
   - 音频是否保留；
   - 颜色变化是否符合 LUT；
6. 记录耗时、发热、OOM/失败信息。

已知边界：

- 完整 RAW 显影未引入 libraw；
- HDR/10-bit、特殊编码可能被 Media3 拒绝；
- 视频导出耗时取决于机型与片段长度。

## 3. 1GB / 批量传输回归（已通过）

用户已确认成功传输 **一个 2.3GB MP4**：

- 大文件路径、前台服务、存储空间预检、50001 下载均通过；
- 未记录精确耗时/平均速度；
- 断点续传和相机主动断开行为仍待单独测试。

LUT 相关内容已在 v2.0.x 移除，本节之前的 LUT 回归清单仅作历史记录。

## 4. 兼容性矩阵（更新）

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| G100D 连接 | 真机通过 | `getstate=ok` |
| 访问批准 | 真机通过 | `ok_under_research_no_msg` → `ok`，无弹窗 |
| 目录 | 真机通过 | `/Server0/CDS_control` 200，66 条 |
| JPEG 传输 | 真机通过 | 多张成功，`Pictures/LumaLink` |
| MP4 传输 | 真机通过 | 多个成功，`Movies/LumaLink` |
| RW2 传输 | 真机通过 | `Download/LumaLink/RAW` |
| RW2 预览 | 真机通过 | 内嵌 JPEG 预览 |
| 实时监看 | 真机通过 | 用户确认 |
| 遥控拍照 | 真机通过 | 用户确认 |
| 录像命令 | 命令 ok | 文件完整性待核对 |
| LUT 照片 | 代码/单测 | 待真机 |
| LUT 视频 | 代码 | 待真机 |
| 1GB 传输 | 未测 | 本轮加入空间预检/超时/重试 |
| 其他品牌手机 | 未测 | 仅小米真机 |

## 4.1 重要：G100D 固件 1.5 修复 Wi-Fi 传输性能

松下官方 DC-G100D 更新页：

- <https://av.jpn.support.panasonic.com/support/global/cs/dsc/download/fts/dl/g100d.html>
- Ver.1.5（2026-06-23）：**Fixed an issue with Wi-Fi transfer performance.**

测试机 `getinfo` 返回 1.0，`getstate` 的 `version` 为 `VD3.80`，明显早于 1.5。
1.1MB/s 很可能是旧固件的传输性能问题，而不是 G100D 的硬件上限。

处理建议：

1. 用户自行按松下官方步骤升级到 Ver.1.5；
2. LumaLink 不做、也不能做固件写入；
3. 升级后重新测同一张 23MB RW2 / 大 MP4；
4. 若仍只有约 1MB/s，再判断为相机 HTTP 服务上限。

## 5. 产物

| 产物 | 路径 |
| --- | --- |
| APK | `dist/LumaLink-1.4.0-debug.apk` |
| 源码 | `dist/LumaLink-1.4.0-src.zip` |
| 校验 | `dist/SHA256SUMS.txt` |

## 6. 结论

照片、视频、RAW 的无线传输与 RAW 预览已完成真机验证。
剩余工作集中在 LUT 真机回归、1GB/批量传输、录像文件完整性和其他品牌手机兼容性；这些需要用户配合实测，代码侧已加入空间预检、较长读超时和三次重试。
