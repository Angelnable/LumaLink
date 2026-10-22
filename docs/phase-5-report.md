# 阶段 5 报告：LUT 导入、照片预览与导出

日期：2026-10-19
版本：1.0.7-debug
状态：**阶段 5a/5b 代码完成**（`.cube` 1D/3D、照片预览/导出、RAW 内嵌预览、视频抽帧、Media3 视频套 LUT 转码）；真实视频转码待真机验证。

## 1. 已实现

### LUT 解析与管理

- `.cube` 解析器：
  - 1D（`LUT_1D_SIZE`）与 3D（`LUT_3D_SIZE`）
  - `DOMAIN_MIN` / `DOMAIN_MAX`
  - `TITLE`、注释、空白行
  - 校验网格大小、数据数量、非有限值、domain 合法性
- LUT 文件复制到应用私有目录 `files/luts`，不依赖外部存储权限。
- Room v3 新增 `luts` 表；从 v2 迁移保留传输队列和相册收藏。
- 列表、重命名、删除、选中；导入失败有明确错误。

### 照片套 LUT

- 使用 Android Photo Picker 选择照片，或 SAF 选择 RAW。
- 预览最大边 1280px，LUT 立方插值（3D）或线性插值（1D）。
- 原图/LUT 切换、进度显示。
- 导出全尺寸 JPEG 到 `Pictures/LumaLink/LUT`，quality 95。
- 错误（解码失败、OOM、LUT 文件丢失）会提示，不伪造成功。

### RAW 策略

- `.RW2` 不直接套 LUT。
- 从 RAW 中扫描最大内嵌 JPEG 预览，套 LUT 后导出 JPG，并明确标注 **“RAW 内嵌预览 · 非 RAW 显影”**。
- 完整 RAW 显影（libraw）不在 v1 默认范围内。

### 视频

- 使用 `MediaMetadataRetriever` 抽取 4 帧，套 LUT 后显示网格。
- **视频转码已接入 Media3 Transformer**：
  - `SingleColorLut.createFromCube` + 自定义 GL LUT 效果
  - H.264 输出，默认保留音频
  - 进度回调，输出到 `Movies/LumaLink/LUT`
  - HDR/10-bit 输入可能被 Media3 拒绝，会显示错误
- 转码尚未在真机上验证；不做“已通过”声明。

## 2. 安全与边界

- LUT 仅处理本地文件；不上传。
- 不修改相机文件。
- RAW 只读取内嵌预览，不做去马赛克/全显影。
- 视频转码未实现时不伪造产物。

## 3. 测试

| 测试类 | 数量 | 内容 |
| --- | --- | --- |
| `CubeLutParserTest` | 5 | 3D identity/invert、1D + domain、错误数据量、缺少 size |
| `LutCubeTest` | 1 | LUT 到 Media3 ARGB cube 的 R/G/B 轴映射 |

当前总计 51 个 JVM 测试通过（protocol 38 + core 5 + transport 2 + lut 6）。

## 4. 未验证

- 真实 `.cube` 文件导入、重命名、删除。
- 20MP 照片全尺寸导出耗时/OOM。
- 真实 RW2 内嵌预览提取。
- 手机相册是否立即索引导出文件。
- 视频抽帧在不同编码/旋转下的表现。
- 视频转码（阶段 5b）。

## 5. 下一步

- 阶段 5b：Media3 Transformer + 自定义 GL LUT 效果，硬件转码并保留音频；先做 10 秒测试片段。
- 阶段 4 遥控/实时取景依赖真实相机只读验证；在连接问题解决后再做。
- 阶段 6 打包/兼容性矩阵/性能基准。
