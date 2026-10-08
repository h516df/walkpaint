# 足迹涂色 · WalkPaint

把平时走过的路径慢慢染色，而不是每次出门都要开运动记录。Android 私人项目，当前 **v0.2.0 测试版**。

## v0.2 重点

- **自动足迹**：首次在 App 内确认、授权精确位置，开启开关后由带常驻通知的定位前台服务持续尝试记录；静止时降低 GPS 采样频率，移动时再提高，减少电量消耗。用户可随时暂停；不是未经授权的静默全天候追踪。
- **看见自己**：地图上的蓝色位置点和“定位我”按钮；首次打开会解释并请求定位权限。MapLibre + OpenFreeMap Liberty 在线地图。用户真实路径蓝绿色，紫色演示路线不计入里程。
- **三大页面**：地图 / 足迹 / 我的。里程、记录天数、探索网格（约半公里级估算）、按日期整理的历史路线；点击足迹可在地图中定位。
- **备份与恢复**：“我的 → 导出备份”用系统文件选择器保存 JSON（可以保存在云盘）；“导入备份”按轨迹 ID 合并，不删除原记录。程序在本地尝试周期性保存快照，但卸载应用会把这些私有本地快照一起删除，**必须另外导出**才算脱离设备的可靠备份。
- **数据兼容**：应用包名仍为 `cn.walkpaint`，`versionCode=2`，调试版沿用 v0.1 同一签名。SQLite 保存位置点；升级时将 v0.1 的 `tracks.json` 迁移并保留源文件，不要求清除旧数据。数据库升级必须显式迁移，禁止默默删表。
- **私密性**：没有足迹上传服务器；加载地图瓦片会向 OpenFreeMap 请求地图区域信息。用户自己的完整轨迹只在设备和用户主动导出的文件中。App 不会自动把 JSON 上传到云端。

## 使用方法

1. 从当前私有 GitHub Release 下载 `WalkPaint-v0.2-debug.apk`，直接覆盖安装 v0.1（不可卸载重装，否则旧数据会消失）。
2. 打开后允许**精确位置**，地图有蓝色当前位置标记，点“⌖”可回到定位点。
3. 在地图或“我的”打开「自动足迹」，确认弹窗。以后不必为每次外出手动启动；记录运行状态会显示在页面和 Android 持续通知中。
4. 为了让**重启后恢复**尽量成功，在系统的应用定位权限中允许“始终允许”；即使如此，Android、厂家电池限制及手动强制停止仍可能中断定位。没有后台授权时，打开 App 可尝试恢复。
5. 在“我的”点击「导出备份」，把 JSON 保存到手机、网盘或其他长期位置。可用「导入备份」合并恢复。
6. 紫色“演示路线”只供试看，可单独清除，不计入真实统计。

### 能做到什么、还做不到什么

当前把真实 GPS 折线叠加在地图上，**不等于精确识别每条道路**；“探索网格”是粗略区域统计，不是城市道路覆盖率。定位点会过滤不准确、超速跳变、静止漂移和异常时间戳；省电采样可能漏掉很短的行走路线。当前路线页按日期显示记录，不包含精确城市/地点地名反向地理编码。

**高德地图**可以接入，但还没有接入。合法接入要求你拥有高德开放平台 Android Key（绑定此应用包名、签名证书），并完成其隐私合规初始化。不能拿非官方瓦片或他人的 Key 来冒充已接入。准备资料在 [docs/AMap_SETUP.md](docs/AMap_SETUP.md)。当前底图仍为 OpenFreeMap。

## 开发与构建

项目核心入口：

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/cn/walkpaint/MainActivity.kt` | UI 入口、权限、地图生命周期与导入/导出流程 |
| `app/src/main/java/cn/walkpaint/Screens.kt` | 三栏页面与 Material 3 组件 |
| `app/src/main/java/cn/walkpaint/RecordingService.kt` | 自动定位前台服务、省电策略与恢复 |
| `app/src/main/java/cn/walkpaint/FootprintDatabase.kt` | SQLite、v0.1 迁移、轨迹存取、备份 |
| `app/src/main/java/cn/walkpaint/Tracks.kt` | 坐标模型、距离/过滤器与 JSON 格式 |
| `app/src/test/java/cn/walkpaint/` | 位置计算、过滤、序列化与统计单元测试 |

本机 Gradle 使用 Android SDK 36、JDK 21、AGP 9.0.1、Gradle 9.1.0 和 Kotlin Compose 2.3.20；正式编译需安装对应依赖。使用项目的 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`。APK 在 `app/build/outputs/apk/debug/app-debug.apk`；正式产品签名、发布与 API Key 不应写在 Git。

**验收状态（2026-10-08）**：模拟器 Android 15 安装与旧版覆盖安装成功，旧轨迹已迁移；地图瓦片和三个页面实际渲染，导出/导入 Android 系统文件选择器可操作；前台定位服务成功启动并显示常驻通知。10 项 JVM 单测通过。真实 Android 手机跨天 GPS、低功耗与长期后台可靠性尚未验收，不能承诺绝对不断线。

## 开源服务及署名

地图：MapLibre Android SDK + OpenFreeMap，© OpenStreetMap contributors。使用公共服务时保留署名与满足服务条款；生产化前复核配额和稳定性。
