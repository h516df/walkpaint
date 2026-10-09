# 足迹涂色 · WalkPaint

Android 私人足迹 App，**v0.6 本地测试版**：在应用内单次同意后下载附近 OSM 道路，也可导入本地文件；GPS 匹配真实道路几何，持久累计走过的道路区间，重复/反向重走不重复计数。青绿为已确认道路，灰色为未涂道路和细 GPS 参考；包外、路口歧义、断档和差精度保持未确认。**尚未完成用户手机户外及长期后台验收。**

## 本地运行

- 手机安装包：[WalkPaint-v0.6-arm64-debug.apk](WalkPaint-v0.6-arm64-debug.apk)，通用包：[WalkPaint-v0.6-universal-debug.apk](WalkPaint-v0.6-universal-debug.apk)。包名 cn.walkpaint，versionCode7，versionName0.6，沿用原签名。最终安装复验当前受模拟器网络/窗口授权阻塞，见 HANDOFF。
- 已有旧版请**覆盖安装**，不要卸载或清除数据。SQLite schema1→2 只增加道路、覆盖和匹配表，保留全部旧 trips/points。旧 schema1 APK 不支持直接降级打开新库。
- 首次主动同意自动记录并授权精确位置后，前台定位服务尝试连续记录，保留常驻通知；“我的”可暂停、查看无坐标诊断。系统省电/强停可能中断，强停后需手动打开。
- 地图顶部或“我的 → 下载 / 查看附近道路”：取得当前位置后，点“下载附近道路”，阅读区域/IP说明并选择“同意并下载本次”；约1.2公里范围的道路保存到本机。之后离线走路、看确认匹配的道路变青绿；新区域需再次下载。没有自动联网刷新、自动重试或足迹上传。也可点“选择道路包文件”打开系统文件选择器。文件必须含合法来源声明、完整 WGS84 LineString、真实 OSM way ID 与版本。详见 [道路包说明](docs/ROAD_PACK.md)。仓库提供 [牛津真实道路测试包](app/src/test/resources/oxford-roads.geojson)，仅覆盖固定公开测试区域，不能给其他地区涂色。
- 导入会重新匹配已有非演示足迹。每包最多8MB / 2000 way；无需服务、账号、Key 或第三方地图匹配上传。支持小区域道路包，不是全球道路库或自动城市下载器。
- 道路损坏或匹配/覆盖写入失败时，仅原子回滚该次道路操作，已接受 GPS 仍保存、后续定位继续；界面显示道路错误，重新导入有效包可修复并回算覆盖。真实 GPS 数据库写入失败仍明确报错，不假称保存。
- 手动 JSON 备份及本机每日快照包含轨迹和道路包；恢复合并并重新计算覆盖，兼容旧版轨迹 JSON。应用不自动导出到设备外；本地快照不能防卸载或丢手机。

## 隐私与许可

默认 MapLibre **离线道路地图**，没有在线瓦片、字体或匹配请求。仅在“我的 → 联网底图 / 离线道路”明确说明并选择“同意联网并记住”后访问 OpenFreeMap；同意保存在本机，后续打开地图会联网，底图服务会收到浏览区域和 IP。可在同一入口选择“使用离线道路”，退出后也保持离线。**不采集、不持久化公共托管瓦片道路要素，不使用瓦片 ID 作道路身份，不上传私人 GPS 来获取道路。**

OSM 道路数据：© OpenStreetMap contributors，按 [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) 使用；来源、限制和真实性边界见 [ROAD_PACK.md](docs/ROAD_PACK.md) 及 [OSM 许可](https://www.openstreetmap.org/copyright)。道路文件提供者须保证来源合法且完整，离线字段校验不能证明声明真实。

可选高德官方 SDK 仍保留；需专属合法 Android Key 和独立 SDK 隐私同意，显示端将 WGS84 转 GCJ-02。**本轮没有高德 Key，没有验收高德底图。** 配置说明：[AMap_SETUP.md](docs/AMap_SETUP.md)。

## 验证与限制

JDK21、SDK36、AGP9.0.1、Gradle9.1.0。构建：gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain。55 项 JVM 测试通过；Android 隔离数据库测试验证 v1 保留升级、道路重复导入/错误回滚、备份恢复与重复 trip 去重。

v0.5 可靠性修复已在原 APK 复现故障，并在最终 APK 的独立 Android 测试库验证：损坏道路不丢点、不锁死后续记录，覆盖中途失败没有部分提交，有效道路重新导入恢复，真实 GPS 写入失败仍报错。仅 MuMu 覆盖安装验收，原83点全部保留，新增1个模拟测试点；最终已安装 APK 指纹、实际截图及离线选择重启验证见 [HANDOFF.md](HANDOFF.md)，本地证据 verification/v05-reliability。

真实入口验收：python scripts/android_v05_acceptance.py --output verification/v05-final，仅允许 MuMu 127.0.0.1:16384 或 emulator-*；覆盖安装、原有行比较、实际系统文件选择器、公开真实道路上的模拟 GPS、累计去重、差精度/断档拒绝、进程重开和实际地图截图。脚本会增加**明确属于模拟器的 GPS 测试点**，不会卸载、清数据或操作用户手机。证据、APK 指纹和实际完成状态见 [HANDOFF.md](HANDOFF.md)。

匹配采用保守投影，不猜跨路口路由；1–20米精度、≤45秒点间隔、每对3–120米、≤8米/秒。路口/平行路/自交处可能少涂。高密度城市、长期数据规模、驾车速度、真实手机定位漂移和 vivo 长期锁屏/耗电尚未验收。不宣称解决系统杀后台，也不宣称用户手机已更新。本轮只本地构建、验证和提交，不推送、不发布。

当前 v0.6 下载初版已在模拟器真实入口通过下载、重复去重、重开保留；断网测试切断了模拟器ADB，清理和最终APK安装复验尚待窗口授权恢复。不能把部分测试称为最终交付。证据及准确APK对应关系见 [HANDOFF.md](HANDOFF.md)。
