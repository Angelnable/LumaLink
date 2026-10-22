# LumaLink

**LumaLink（流光图传）** 是面向 Panasonic LUMIX 相机的第三方 Android 文件传输工具。

它只做两件事：**连接相机**、**浏览并传输相机文件**。不包含相册、实时取景、遥控拍摄或 LUT 功能。

> 当前版本：**4.3.11**（versionCode 97）  
> 已真机验证：Panasonic LUMIX **G100D**、**S5M2（S52）**

## 功能

- Wi-Fi 扫描与手动 SSID，优先 5GHz / Wi-Fi 5
- LUMIX `accctrl` 访问确认，兼容 legacy 与 challenge 两种模式
- 相机文件浏览：列表 / 网格、全部 / 照片 / 视频 / JPG / RAW 筛选
- 批量选择、全选 / 取消全选、下载选中
- 预览默认关闭；全部文件读取完成后可开启
- 单张预览与“全部预览”，支持上一张 / 下一张
- 传输队列、前台服务、进度 / 速度 / ETA、取消 / 重试
- HTTP Range 断点续传、SHA-256 校验、MediaStore 保存
- 拍摄日期读取：UPnP `dc:date` → EXIF `DateTimeOriginal` → `Last-Modified`
- 诊断日志：连接、HTTP、Browse、传输过程可查看 / 复制 / 清空

## 文件读取说明

S5M2 的 Browse 服务有几个实际限制：

- 每页最多返回 **50 条**，即使请求更多；
- 每次访问授权大约只能读 **3 页 / 150 条**，之后返回 403；
- 403 后需要重新授权，再从同一个 `StartingIndex` 继续；
- 尾页可能返回超量数据（例如总数 605，最后一页却返回 70 条），需要补取尾页；
- 同一文件的不同 `res` URL 可能重复出现，需要按真实文件名去重。

LumaLink 的处理顺序：

```text
请求访问 / playmode
  → Browse 每页 50 条
  → 403 时重新授权，从同一 startIndex 重试
  → 读到相机报告的总数
  → 从 total-50 补取最后一页
  → 按真实文件名去重
  → 多于 total 时保留最新的 total 项
  → 显示“已读取 N 项”
```

当前限制：

- 单次最多读取 **10000 项**；
- 最多允许 **100 次 403 重授权**，按每次 150 条可覆盖约 15150 项；
- 10000 项首次扫描约需 8–25 分钟，取决于相机响应；
- 再次打开文件页会复用已读取列表，只有点刷新才重新扫描；
- 预览关闭时不加载缩略图，开启后只为当前可见项懒加载。

## 支持机型

**已真机验证：**

- Panasonic LUMIX G100D（固件 1.50）
- Panasonic LUMIX S5M2（S52，固件 3.74；照片 / 视频传输成功）

**LUMIX Sync 列表（未真机验证）：**

- S1 / S1R / S1H / S5 / S5M2X / GH5M2 / GH6 / GH7 / G9M2 / S9

**Image App 列表（未真机验证）：**

- GF6 / G7 / GH4 / GX85 / GX80 / LX100 / TZ220
- GH5 / GH5S / G9 / GX7MK3 / GX880 / LX100M2
- TZ99 / ZS99 / TZ300 / FZ1000M2 / G99 / G99D

未验证机型不保证相同的 `accctrl` / Browse / 50001 行为。

## 构建

需要 JDK 17、Android SDK 35 和 Gradle 8.9（或使用项目自带 wrapper）。

```bash
git clone https://github.com/Angelnable/LumaLink.git
cd LumaLink

# Windows
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain

# Linux / macOS
./gradlew :app:assembleRelease --no-daemon --console=plain
```

输出：

```text
app/build/outputs/apk/release/app-release.apk
```

调试构建：

```bash
./gradlew :app:assembleDebug
```

单元测试：

```bash
./gradlew :protocol:test :core:testDebugUnitTest
```

`local.properties` 需要指向本机 Android SDK，例如：

```properties
sdk.dir=/path/to/Android/Sdk
```

该文件已被 `.gitignore` 忽略。

## 工程结构

```text
app/          Application、MainActivity、导航、设置 / 关于
core/         领域模型、Room、DataStore、诊断、格式化
protocol/     LUMIX HTTP 协议、访问授权、UPnP Browse、下载（纯 Kotlin，可 JVM 测试）
transport/    Wi-Fi 绑定、连接 / 传输页面、前台传输服务
ui/           Material 3 设计系统与通用组件
docs/         协议、架构、兼容性、测试记录
scripts/      工具链与打包脚本
```

依赖方向：`app → transport / ui / core / protocol`；`protocol` 不依赖 Android UI。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk | 35 |
| minSdk | 29（Android 10） |

## 安全边界

- 不刷写固件、不写寄存器、不改系统分区、不绕过签名；
- 未获得明确授权前不连接相机；
- 默认只读：文件浏览只使用 GET / HEAD / Browse；
- 不删除相机文件；
- 访问授权和传输状态由 `CameraAccessGate` 串行化，避免传输与 Browse 互相顶掉；
- 所有“待验证”能力不伪造画面、进度或测试结果。

## 下载

APK 发布在 [GitHub Releases](https://github.com/Angelnable/LumaLink/releases)。

当前版本校验和会随 Release 一并提供。

## 免责声明

LumaLink 是非官方第三方工具，与 Panasonic 无隶属关系。Panasonic、LUMIX 是松下集团的商标，仅用于说明兼容设备。请仅用于个人设备互操作，遵守当地法律与松下相关条款。

## License

Apache License 2.0，见 [LICENSE](LICENSE)。
