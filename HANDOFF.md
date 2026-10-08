# HANDOFF — 足迹涂色

**日期：** 2026-10-08 ｜ **项目：** `D:\Projects\WalkPaint`

## 用户目标
在 Android 手机上像给地图涂色一样，逐步累计自己真正走过的道路，未来需道路吸附与覆盖率进度。首版只做用户主动开启的 GPS 路线彩色叠加，不虚报整条路已涂色。

## 现场
- 独立本地 Git 仓库（不是 `MyFirstApp`），Gradle Kotlin/AGP + MapLibre Android SDK 13.5.2，OpenFreeMap Liberty 底图，无付费 API key。
- Kotlin：`Tracks.kt`（状态、过滤、JSON原子写入），`WalkPaintApp.kt`，`RecordingService.kt`（显式开始/结束的 Location FGS），`MainActivity.kt`（地图图层、控件、演示与统计）。
- `app/src/test/java/cn/walkpaint/TracksTest.kt` 共 6 项单测。
- 构建需要 `JAVA_HOME` 指向本机 JDK21，`GRADLE_USER_HOME` 可用项目下 `.gradle-user-home`；Kotlin 编译设置 in-process 可避免 Codex 沙箱中的 daemon 路径拒绝访问。退出码与最终 APK 见实际构建测试产物，不能仅依赖本文件。
- 调试包根目录 `足迹涂色-v0.1-debug.apk`；原始 Gradle 输出 `app/build/outputs/apk/debug/app-debug.apk`。

## 下一步
1. 优先用**真实 Android 手机**测试地图在线加载、允许精确位置、GPS 在户外收到点、锁屏持续记录、通知停止、重开后轨迹仍在；留截图/日志再宣称真机可用。本机 ADB 无连接设备；尚无实际设备验收。
2. 为真实长期使用添加 GPX/JSON 导入导出与恢复/备份（v0.1 清除 App 数据、卸载会丢失）。
3. 迭代道路匹配（OSM 路网/地图匹配，精度和覆盖率真实性要区分），重复道路去重与探索进度；避免仅按轨迹长度宣称已走道路比例。
4. 测试 vivo/Android 后台电池策略；前台服务不等于所有机型都保证不被杀。
5. 后续源码改动遵循 Git 单写手和隔离规则；不碰别的项目的签名文件，不公开地图轨迹，不使用付费 API，除非另获授权。

## 安全边界
用户轨迹不上传业务服务器；底图向 OpenFreeMap 请求地图数据，须持续显示归属。无 `ACCESS_BACKGROUND_LOCATION`，仅主动点击后启用 Android 定位前台服务。不绕过 ChatGPT 平台安全审核、不关闭 Windows 防护。
