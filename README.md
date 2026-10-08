# 足迹涂色 · WalkPaint

Android 私人足迹涂色 App。日常自动累积走过的路线；当前 **v0.3.0 预览版**，尚未经真机长期续航验收。

## v0.3 重点

- **首次同意，授权即开始**：阅读“同意并开启自动足迹”后授予精确定位，就立即尝试开启有常驻通知的定位前台服务；不需再次点开始。拒绝不会偷偷开启，“我的”可暂停。
- **短途立即反馈**：首个准确 GPS 点显示蓝绿色种子标记，多个已接受的真实点绘制线路；展示精度、点数、最近入库时间和 GPS 异常、过滤原因。不会假装画出未采集的路线。
- **正常导航图标**：地图、足迹、我的采用矢量图标，“我的”是人物图标。
- **性能与历史数据**：Indexed SQLite 最近点查询，异步历史快照与 GeoJSON；兼容 v0.1/v0.2 轨迹和调试版签名，versionCode=3，包名 cn.walkpaint。
- **地图**：加入高德 2026 官方 Maps SDK、单独第三方隐私授权和只在显示端进行 GPS(WGS84)→GCJ-02 转换。没有合法 Android Key 时继续 MapLibre/OpenFreeMap；腾讯、百度为候补，尚未集成。
- **备份**：JSON 可导出和合并导入；本地自动快照随卸载消失，仍需主动备份到设备外。

## 安装与使用

1. 从私有 GitHub Release 下载 v0.3。常规 arm64 安卓手机优先 WalkPaint-v0.3-arm64-debug.apk（约 70 MB）；其他 ABI 使用 WalkPaint-v0.3-universal-debug.apk（约 125 MB）。
2. **直接覆盖安装** v0.1 或 v0.2，不要卸载或清除数据。签名与先前测试版一致。
3. 首次阅读“自动足迹”说明并主动同意，随后授权精确位置，录制就会立即尝试开始。地图蓝点是自己，蓝绿色是记录足迹，紫色仅演示。
4. 在“我的”查看里程、天数、探索网格、跟踪状态、导入/导出。后台重启自动恢复通常还需系统权限“始终允许定位”，以及按机型确认电池策略。
5. Android 省电、强制停止、GPS 环境可能影响持续采集；App 会尽量说明状态，但不能承诺永不间断。

## 高德地图与隐私

官方依赖版本 **com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1**。高德仅在已配置受签名限制的官方 Android Key 且用户**单独同意**高德 SDK 隐私信息后初始化；拒绝时可继续在开放地图上记录本地足迹。说明见 docs/AMap_SETUP.md。

**没有有效高德 Key，因此高德底图未被实测成功。** SDK 可能访问网络、定位、设备/系统与传感器信息，APP 内提供隐私说明和高德隐私政策链接。足迹仍在本地 SQLite/JSON 存为 WGS84，不将历史数据库自动上传至我方服务器；第三方在线地图会收到必要地图区域及网络请求信息。

## 已知限制 / 下一阶段

- 目前是 GPS 点与折线叠加，**不是真正把道路吸附并永久去重涂色**；探索网格不等于道路覆盖率。地点城市统计、重复道路识别尚未实现。
- 真机长期锁屏、省电、重启、定位漂移、安卓厂商限制以及多年数据量性能未测试，需真实手机验证。
- 真正独立于手机本地数据的定时云备份尚未实现。
- 腾讯/百度为必要时备用地图服务，不使用无授权瓦片或他人的 Key。

## 开发与验证

主要文件：MainActivity.kt（权限、Compose/地图生命周期）、Screens.kt（3 页）、RecordingService.kt（自动定位）、FootprintDatabase.kt 与 Tracks.kt（数据与过滤）、TrackMapJson.kt（点/路线格式）、AmapMapController.kt（官方高德显示层）。默认地图仍是 MapLibre + OpenFreeMap，署名 © OpenStreetMap contributors；高德启用后署名 © 高德。

Android SDK 36、JDK21、AGP9.0.1、Gradle9.1.0；全量构建运行：gradlew.bat :app:testDebugUnitTest :app:assembleDebug；手机 arm64 精简构建：gradlew.bat :app:assembleDebug -PwalkpaintArm64Only=true。构建 APK 位于 app/build/outputs/apk/debug/app-debug.apk。API Key、用户足迹与签名私钥绝不提交到 Git。

**2026-10-08 验收**：14 项 JVM 单元测试通过。MuMu Android15 模拟器覆盖安装并实际点击首次同意，系统显示定位前台服务 isForeground=true；注入 3 个模拟短途 GPS 点，SQLite 真实定位点数从 3→6，地图显示蓝绿色足迹；测试完撤销 Mock Location 权限及测试提供器。真机及有 Key 的高德底图尚未验证。
