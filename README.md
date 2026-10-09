# 足迹涂色 · WalkPaint

Android 道路探索涂色应用：授权一次，日常自动记录；走过的真实道路逐渐上色。首页近全屏高德地图，柔和青绿道路、小蓝点、定位按钮和轻薄导航；成果在足迹，记录管理和诊断在我的。重复经过去重，歧义/漂移/断档保守拒绝；不显示 GPS 折线或用演示轨迹冒充涂色。

## v0.12 本地候选（简化我的与授权）

一个自动记录开关，涂色颜色和更多设置；专业能力进入二级，系统定位/后台/通知按序引导。111 JVM、7 设备保护、Google API36 实际 UI/1→4 点入库/短时后台与关屏/关闭停止/320dp通过。原服务与数据规则保持，反馈/更新按钮尚未开发。候选未发布/未在手机安装，原 v0.11 下载继续有效。[验收、截图、黑屏基线限制与回退](docs/evidence/V012.md)。真实 vivo P0 和地图像素验收仍未完成，不称全部产品已完成。

## v0.11 私有测试下载（道路涂色外观）

我的 → 涂色外观新增青绿、雾蓝、灰紫、石墨、赭金五种预设，支持六位颜色代码与恢复默认。旧的已确认道路立即换色，选择保存在本机；相机/跟随和灰色未走道路/蓝点保持。高德与离线实际入口、103项JVM、小屏/重开/同签名覆盖安装、重复走路去重通过。[实际截图与验收边界](docs/evidence/V011.md)。

[手机 APK（约70MB）](https://github.com/h516df/walkpaint/releases/download/v0.11/WalkPaint-v0.11-arm64-debug.apk) · [私有预览发布](https://github.com/h516df/walkpaint/releases/tag/v0.11)。用户本轮明确确认后已分发，服务器大小/digest与实际下载指纹一致。手机浏览器登录有权限GitHub账号后下载、覆盖安装并打开一次，勿卸载/清库。code12/schema2/原签名；**手机是否安装尚未核实**。[草稿 PR #15](https://github.com/h516df/walkpaint/pull/15)，已推送、未合并，依赖未合并PR #13；无GitHub Actions，不称CI通过。真实vivo颜色、方向、长期后台和原GPS P0仍未验收。覆盖安装，不卸载/清库；回退需同签名code>=12/schema2修复包。

## v0.10 私有测试下载（地图跟随）

[草稿 PR #13](https://github.com/h516df/walkpaint/pull/13)，不合并；本仓库未配置GitHub Actions，当前验证为本机自动测试与真实模拟器入口。

自动跟随可靠位置和手机朝向，保留缩放；拖动浏览暂停，点原“定位我”恢复，历史地图不被实时定位拉走。高德与离线回退实际UI均通过；100项JVM、16组Android屏幕方向计算、实际SDK角度/缩放和前后台监听专项通过。[真实前后截图与验收边界](docs/evidence/V010.md)。

[手机 APK（约70MB）](https://github.com/h516df/walkpaint/releases/download/v0.10/WalkPaint-v0.10-arm64-debug.apk) · [私有预览发布](https://github.com/h516df/walkpaint/releases/tag/v0.10)。用户本次明确确认后已上传，服务器digest/大小与本地及实际重新下载文件一致。手机登录有权限GitHub账号后下载、直接覆盖安装、打开一次，勿卸载/清数据。code11/schema2/原签名保留旧足迹。**手机旋转与原GPS P0尚未验收，用户手机是否安装尚未核实**。#7源码在独立 `issue-7-map-follow`，依赖#9/v0.9基线，不合并。回退需同签名、schema2、code>=11修复包，勿卸载/清数据。

## v0.9 私有测试候选（GPS 核心修复与诊断）

[草稿 PR #9](https://github.com/h516df/walkpaint/pull/9)。本地 `WalkPaint-v0.9-arm64-debug.apk` 已完成同签名覆盖升级及真实模拟器验收，**v0.9 APK 未上传、Release 未创建，私有下载待本次上传确认**。修复GPS健康检查异常中断，新增本次回调→有效GPS→入库计数、无坐标卫星状态及道路拒绝细分。安装时应覆盖升级，勿卸载/清数据。

94项JVM、Android隔离库与MuMu实际安装包全链通过；[完整前后截图与验收边界](docs/evidence/V09.md)。**用户vivo原无GPS回调与真实道路连续涂色尚未验收，#6保持开放**。不修改道路匹配门槛，不用network蓝点冒充已走道路。

## v0.8 私有测试下载

[手机 APK（约70MB）](https://github.com/h516df/walkpaint/releases/download/v0.8/WalkPaint-v0.8-arm64-debug.apk) · [私有预览发布](https://github.com/h516df/walkpaint/releases/tag/v0.8)。手机浏览器登录有权限 GitHub 账号后下载，直接同签名覆盖升级，勿卸载/清数据。安装后打开一次完成系统或独立地图 SDK 授权。只需手机操作，不需要到电脑旁或接 USB。

code9 / version0.8 / schema2 / 原签名 / arm64。87项JVM、Android隔离库和MuMu实际UI/短时后台/小屏验收通过；[真实截图与测试边界](docs/evidence/V08.md)、[交接](HANDOFF.md)。**vivo 户外/长期后台以及真机79点、76未确认、0米的原因仍未验收，Issue #2 保持开放。** 源码 feat/v08-map-first，依赖尚未合并的PR #3/#1，未从旧main重做。

## 下载与运行

- 历史v0.7：[v0.7 APK](https://github.com/h516df/walkpaint/releases/download/v0.7/WalkPaint-v0.7-arm64-debug.apk)；[私有发布页](https://github.com/h516df/walkpaint/releases/tag/v0.7)。真实后台仍待验收，旧足迹通过覆盖安装保留，勿先卸载。
- 历史v0.6存档：[v0.6 APK（约70MB）](https://github.com/h516df/walkpaint/releases/download/v0.6/WalkPaint-v0.6-arm64-debug.apk)；[私有发布页](https://github.com/h516df/walkpaint/releases/tag/v0.6)。在手机浏览器登录有权限的GitHub账号后下载。
- 本机安装包：[WalkPaint-v0.6-arm64-debug.apk](WalkPaint-v0.6-arm64-debug.apk)，通用包：[WalkPaint-v0.6-universal-debug.apk](WalkPaint-v0.6-universal-debug.apk)。包名 cn.walkpaint，versionCode7，versionName0.6，沿用原签名。最终手机包已在MuMu覆盖安装与真实下载入口验收通过；真机后台尚未验收，见 HANDOFF。
- 已有旧版请**覆盖安装**，不要卸载或清除数据。SQLite schema1→2 只增加道路、覆盖和匹配表，保留全部旧 trips/points。旧 schema1 APK 不支持直接降级打开新库。
- 首次主动同意自动记录并授权精确位置后，前台定位服务尝试连续记录，保留常驻通知；“我的”可暂停、查看无坐标诊断。系统省电/强停可能中断，强停后需手动打开。
- “我的 → 附近道路”：取得当前位置后，点“下载附近道路”，阅读区域/IP说明并选择“同意并下载本次”；约1.2公里范围的道路保存到本机。之后离线走路、看确认匹配的道路变青绿；新区域需再次下载。没有自动联网刷新、自动重试或足迹上传。也可点“选择道路包文件”打开系统文件选择器。文件必须含合法来源声明、完整 WGS84 LineString、真实 OSM way ID 与版本。详见 [道路包说明](docs/ROAD_PACK.md)。仓库提供 [牛津真实道路测试包](app/src/test/resources/oxford-roads.geojson)，仅覆盖固定公开测试区域，不能给其他地区涂色。
- 导入会重新匹配已有非演示足迹。每包最多8MB / 2000 way；无需服务、账号、Key 或第三方地图匹配上传。支持小区域道路包，不是全球道路库或自动城市下载器。
- 道路损坏或匹配/覆盖写入失败时，仅原子回滚该次道路操作，已接受 GPS 仍保存、后续定位继续；界面显示道路错误，重新导入有效包可修复并回算覆盖。真实 GPS 数据库写入失败仍明确报错，不假称保存。
- 手动 JSON 备份及本机每日快照包含轨迹和道路包；恢复合并并重新计算覆盖，兼容旧版轨迹 JSON。应用不自动导出到设备外；本地快照不能防卸载或丢手机。

## 隐私与许可

默认 MapLibre **离线道路地图**，没有在线瓦片、字体或匹配请求。仅在“我的 → 联网底图 / 离线道路”明确说明并选择“同意联网并记住”后访问 OpenFreeMap；同意保存在本机，后续打开地图会联网，底图服务会收到浏览区域和 IP。可在同一入口选择“使用离线道路”，退出后也保持离线。**不采集、不持久化公共托管瓦片道路要素，不使用瓦片 ID 作道路身份，不上传私人 GPS 来获取道路。**

OSM 道路数据：© OpenStreetMap contributors，按 [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) 使用；来源、限制和真实性边界见 [ROAD_PACK.md](docs/ROAD_PACK.md) 及 [OSM 许可](https://www.openstreetmap.org/copyright)。道路文件提供者须保证来源合法且完整，离线字段校验不能证明声明真实。

可选高德官方 SDK 仍保留；需专属合法 Android Key 和独立 SDK 隐私同意，显示端将 WGS84 转 GCJ-02。**本机 Key 已配置且 v0.8 SDK 模拟器真实渲染通过，vivo 与中国道路实际偏移仍未测。** 配置说明：[AMap_SETUP.md](docs/AMap_SETUP.md)。

## 验证与限制

JDK21、SDK36、AGP9.0.1、Gradle9.1.0。构建：gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain。59 项 JVM 测试通过；Android 隔离数据库测试验证 v1 保留升级、道路重复导入/错误回滚、备份恢复与重复 trip 去重。

v0.5 可靠性修复已在原 APK 复现故障，并在最终 APK 的独立 Android 测试库验证：损坏道路不丢点、不锁死后续记录，覆盖中途失败没有部分提交，有效道路重新导入恢复，真实 GPS 写入失败仍报错。仅 MuMu 覆盖安装验收，原83点全部保留，新增1个模拟测试点；最终已安装 APK 指纹、实际截图及离线选择重启验证见 [HANDOFF.md](HANDOFF.md)，本地证据 verification/v05-reliability。

真实入口验收：python scripts/android_v05_acceptance.py --output verification/v05-final，仅允许 MuMu 127.0.0.1:16384 或 emulator-*；覆盖安装、原有行比较、实际系统文件选择器、公开真实道路上的模拟 GPS、累计去重、差精度/断档拒绝、进程重开和实际地图截图。脚本会增加**明确属于模拟器的 GPS 测试点**，不会卸载、清数据或操作用户手机。证据、APK 指纹和实际完成状态见 [HANDOFF.md](HANDOFF.md)。

匹配采用保守投影，不猜跨路口路由；1–20米精度、≤45秒点间隔、每对3–120米、≤8米/秒。路口/平行路/自交处可能少涂。高密度城市、长期数据规模、驾车速度、真实手机定位漂移和 vivo 长期锁屏/耗电尚未验收。不宣称解决系统杀后台，也不宣称用户手机已更新。代码分支及手机安装包已按用户授权上传私有GitHub；草稿PR未合并，v0.6为预览发布，用户真机验收尚未完成。

历史 v0.6 最终手机包已在模拟器真实入口通过下载、重复去重、重开保留、GPS保存和覆盖升级；清理与安装指纹实读通过。59项测试，真机长期后台/国内网络可达性尚未验收；不能称用户手机已更新。证据及准确APK对应关系见 [HANDOFF.md](HANDOFF.md)。
