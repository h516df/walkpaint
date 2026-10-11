> 当前发布：[v0.32 私有探索迷雾预览](https://github.com/h516df/walkpaint/releases/tag/v0.32)。在“我的→检查更新”下载并由系统覆盖安装；原签名/code34/schema2。联网模拟器实际UI及旧表保持通过，用户vivo/ARM高德尚待验收，详见 [最新交接](HANDOFF.md) 与 [发布验收](docs/evidence/V032_PRIVATE_RELEASE.md)。

# 当前版本：v0.16 高德联网直接轨迹涂色

无需准备地图；已保存GPS直接涂色，步行、骑行和正常驾车共用记录。186 JVM/7 guard及Google真实直接轨迹、历史、换色、联网备用底图、重开、320dp和旧数据保留通过；code18/schema2/原签名手机候选已生成。[证据与手机验收限制](docs/evidence/V016_DIRECT_TRAILS.md)。**私有预览已发布，手机是否安装未核实，新ARM高德/真实驾车和长期后台仍未验收。** [发布页](https://github.com/h516df/walkpaint/releases/tag/v0.16) · [手机APK（约70MB）](https://github.com/h516df/walkpaint/releases/download/v0.16/WalkPaint-v0.16-arm64-debug.apk)。服务器与实际下载SHA一致，覆盖安装保留旧数据；已有版本从“我的→更多设置→检查更新”进入。旧v0.15下载保留。

---

# 历史版本：v0.15诊断增强私有预览

五类GPS过滤计数与最近30分钟/2小时线索已在专用Google实际高级诊断/反馈入口验收；180 JVM、7设备守卫及原五表保留通过。code17/schema2/原签名arm64候选在本chat outputs。**私有预览已开放，用户手机安装和行车涂色修复仍未验收。**[本轮证据](docs/evidence/V015_DIAGNOSTICS.md)；可信速度桶与完整保存→匹配→渲染时窗继续见已有#10，不把本阶段当全部完成。[私有发布](https://github.com/h516df/walkpaint/releases/tag/v0.15) · [手机APK（约70MB）](https://github.com/h516df/walkpaint/releases/download/v0.15/WalkPaint-v0.15-arm64-debug.apk)；已核对服务器与实际下载SHA。直接覆盖安装，不卸载或清数据；已有v0.14可从“我的→更多设置→检查更新”进入。旧v0.14私人下载保持。

---

# 足迹涂色 · WalkPaint

Android 足迹涂色应用：授权后自动记录步行、骑行和驾车，已保存轨迹直接涂色到应用内高德联网地图，无需准备地图或下载道路包。足迹按日期查看，颜色可自选，数据保存在本机。定位中断不补画路线，重复经过按记录里程累计。

## v0.14 私有预览（应用内更新闭环通过，手机未验收）

“我的 → 更多设置 → 检查更新”支持本人 GitHub 设备授权、检查私有新版、中文说明、下载与取消、安装包校验，再由 Android 系统确认覆盖安装。更新应用只读取 h516df/walkpaint，授权本机加密保存，可退出；无需服务器或复制电脑凭据。

171 JVM/7 守卫；专用 Google 模拟器真实私有下载、取消/重试、拒绝来源权限、取消系统安装、确认覆盖、安装后最新版检查、原五表保留，以及授权/密钥/缓存清理全部通过。[分阶段证据、截图与回退](docs/evidence/V014.md)。[私有预览](https://github.com/h516df/walkpaint/releases/tag/v0.14) / [手机 arm64 APK](https://github.com/h516df/walkpaint/releases/download/v0.14/WalkPaint-v0.14-arm64-debug.apk)，服务器与重新下载 SHA 一致。直接覆盖安装，不卸载/清数据。code16/schema2/原签名；[草稿 PR #21](https://github.com/h516df/walkpaint/pull/21) 未合并，手机是否安装、vivo 户外/长期后台/ARM 高德仍未验收。旧 v0.13 下载保留。

## v0.13 历史私有预览（本机已验收，手机未验收）

延续简洁我的/分步授权，新增本机问题反馈与预览分享、中文本版更新说明和私有发布页浏览器入口；不兼容高德的设备使用已有离线地图。129 JVM/7守卫、Google真实反馈全链/同签名覆盖旧行保留/实际公开道路50.04米彩路/后台及短时锁屏通过。[证据、失败修正、截图与回退](docs/evidence/V013.md)。code14/schema2/原签名，手机未安装、vivo户外/长期后台仍未验收，更新当前为浏览器降级路径。按本轮“做完再上传”要求已统一[私有分发](https://github.com/h516df/walkpaint/releases/tag/v0.13) / [手机APK](https://github.com/h516df/walkpaint/releases/download/v0.13/WalkPaint-v0.13-arm64-debug.apk)，服务器与实际下载SHA一致；原v0.11保留，v0.12不另发布。

## v0.12 历史本地候选（简化我的与授权）

一个自动记录开关，涂色颜色和更多设置；专业能力进入二级，系统定位/后台/通知按序引导。111 JVM、7 设备保护、Google API36 实际 UI/1→4 点入库/短时后台与关屏/关闭停止/320dp通过。原服务与数据规则保持，反馈/更新按钮尚未开发。[草稿 PR #16](https://github.com/h516df/walkpaint/pull/16)已保存、未合并。候选未发布/未在手机安装，原 v0.11 下载继续有效。[验收、截图、黑屏基线限制与回退](docs/evidence/V012.md)。真实 vivo P0 和地图像素验收仍未完成，不称全部产品已完成。

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
