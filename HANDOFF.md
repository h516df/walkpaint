# HANDOFF — v0.5 真实道路涂色本地交付（2026-10-09）

> 当前授权是继续开发并本地交付；本节优先于下方历史暂停及 v0.4 记录。唯一写手，工作目录 D:\Projects\WalkPaint，分支 feat/v05-real-road-coverage，起点 5446e20c2efbde480498159e7f21cb739879b874（v0.4）。本地可运行道路切片已验证；没有 push、release、付费服务、第三方足迹上传或用户手机操作。最终本地提交 SHA 请以该分支 git log -1 为准。

## 已实现

- Android 系统 OpenDocument 选择本地 OSM ODbL GeoJSON，先校验整包再事务导入。校验 WGS84、完整 LineString、正整数 OSM way ID / version、highway、来源及许可声明，拒绝公共瓦片源与异常尺寸。导入器不能离线证明提供者的来源声明真实；格式、使用权和真实 fixture 来源见 [ROAD_PACK.md](docs/ROAD_PACK.md)。
- 持久身份是 OSM way ID + 规范方向完整几何 SHA256；保存实际 OSM 版本。反向/重复导入身份一致，同 way 几何改变则更新道路、从原轨迹重新匹配，不复用旧距离轴。删除了公开瓦片道路探测，不 querySourceFeatures、不采集/保存 OpenFreeMap 要素或瓦片 ID。
- RoadPaint.kt 接到实际定位入库路径；RoadCatalog 是有容量上限的本地网格索引，沿候选真实曲线收集全部附近道路并每≤5米检查内部交叉/自交歧义。精度1–20米、同 trip/segment、点间隔≤45秒、每对3–120米、≤8米/秒、路程/直线比约束；不猜跨路口路由。严格约束可能少涂，户外误匹配仍需真机实走评估。
- schema1→2 仅增加 roads、coverage、road_matches，原 trips/points 及 row IDs 不动。定位点、匹配状态、覆盖区间同一事务提交；累计区间取并集，重复/反向/跨天不重复算长度，未走区间留白，演示 trip 不参与道路覆盖。
- MapLibre 实际显示灰色完整道路与青绿色覆盖区间；GPS 是灰色细参考，位置蓝点单独显示。展示累计涂色米数 / 已导入道路长度、way 数与未确认定位段，不把包外区域当完成。高德显示层也接到道路几何，但无 Key，未验收。
- 默认真正离线道路样式，无在线瓦片/字体源。联网底图须本次单独同意发送视口区域/IP；本轮验收全用离线地图，没有上传私人 GPS 或下载私人区域。高德保留原独立隐私同意。
- 本机自动快照及手动 JSON 备份包含轨迹、道路包；恢复兼容旧轨迹 JSON，合并后重新计算覆盖。修复同一导入文件内重复 trip ID 可导致重复 points 的问题。没有实际导出任何用户数据库/轨迹到设备外；备份往返测试用独立 UUID cache 数据库。

## APK、签名与构建

- 最终 APK：[app-debug.apk](app/build/outputs/apk/debug/app-debug.apk)，cn.walkpaint，versionCode6，versionName0.5，schema2。
- SHA256：8c75e08553ed41ad8e55719c470306581cf74f9da2bce5eb4776d2da59f4ef6d。
- apksigner verify --print-certs 成功；证书 SHA256：21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967，与 v0.4 一致。保留的旧 APK：verification/v05/v04-before.apk，旧 APK SHA256 468a7e3b17011dca9aac603bf4d8438efa92cd0ae1b33fae4ef81e6260ee4eed。
- JDK21 / SDK36 / AGP9.0.1 / Gradle9.1.0；testDebugUnitTest、assembleDebug、assembleDebugAndroidTest 均通过。最终构建日志 verification/v05-final/final-gradle.log；**41 项 JVM 单测，失败0、错误0**。包含真实 OSM fixture、4米 GPS 偏移投影、反向重复、交叉口内部桥接拒绝、极高纬索引跨格交叉、精度/断档/速度等。

## 真实入口验证与证据边界

- 仅 MuMu 127.0.0.1:16384，Android15/API35；它是模拟器，**不是 vivo 真机**。脚本固定拒绝其他手机串号，不卸载、不清数据、不重建已有数据库。SQLite 快照只在内存比较，未输出原始私人坐标；verification、APK、缓存忽略 Git。
- 原 v0.4 已安装 schema1、48点；首次真实升级运行 schema2 后所有原 trips/points 全行保留，记录 verification/v05/v1-upgrade-evidence.json。该次后续差精度测试数据设置失败，不能把该次当完整链路通过。最终 APK 的 v1→v2 迁移又在独立 Android 测试库验证，保留测试 trip、两点及 IDs17/18；没有对真实库降级或伪造旧行。
- 完整道路链路：python scripts/android_v05_acceptance.py --output verification/v05-final。**V05_REAL_ROAD_EMULATOR_PASS**；真实系统文件选择器导入6条 OSM 官方来源道路（总长约486米），注入偏离道路4米的公开测试 GPS，累计42.643米、反向/重复不增加覆盖；25米精度被录制器接受而道路匹配拒绝；>45秒断档不桥接；强停手动重开和重复导入保持累计；原70点全保留，新增12点到82。真实地图表面3441个道路色像素，并人工查看 [最终道路截图](verification/v05-final/final-road-map.png)，灰色 GPS 与青绿道路实际分开。结果 verification/v05-final/acceptance.json，cleanup_errors=[]。
- 完整链路对应 APK SHA256 **b2d4ea17fd10c8dd1cf2481154a40d7206a08c1349a33d0ab8803f90b6f6a0fc**。之后仅修正3处 UI 文案：不承诺任何保存点都会涂色、空列表说明道路包前提、演示明确为 GPS。道路/数据库/定位算法未再改变。
- **最终 APK 复验通过**：python scripts/android_v05_acceptance.py --quick --output verification/v05-delivery，V05_FINAL_APK_SMOKE_PASS。覆盖安装最终指纹 APK，真实文件选择器再次导入；最终 APK 的隔离数据库升级、错误回滚、备份往返、重复 trip 去重测试成功；82→83新增1个模拟 GPS 点，82个原点及所有 trip/point 行保留，覆盖仍42.643米；3442个真实道路色像素，并人工查看 [最终 APK 截图](verification/v05-delivery/final-apk-map.png) 和 DOM，确认“仅确认匹配的道路上色”实际显示。结果 verification/v05-delivery/acceptance.json，cleanup_errors=[]；本次没有重跑完整长断档链路，边界如上。
- 当前模拟器83点，相对本任务开始48点增加35个明确的模拟器测试点；每轮保留全部旧行。最终 MOCK_LOCATION=default，测试 GPS provider 已移除，旋转/定位设置恢复。Android 测试 APK 留在模拟器；未操作任何手机。
- Android 独立测试记录 verification/v05-delivery/database-tests.txt（V05_ISOLATED_DATABASE_PASS）：v1迁移、原测试行/ID、重复道路包、无效许可整包不改动、备份恢复相等、重复文件及重复 trip 不产生重复 points、integrity_check=ok。测试使用 UUID-named cache，不清理用户数据。收尾发现 finish() 会先终止进程，使 finally 清理来不及执行；已将关闭测试库、刷盘及删除测试 prefs/cache 前移到 finish() 之前。最后只重建测试 APK（产品 APK 指纹不变），重跑独立数据库测试并核对无遗留测试 cache / MOCK_LOCATION=default，证据 verification/v05-delivery/database-tests-cleanup.txt 和 cleanup-check.log。早期4个本轮测试 UUID 目录先检查仅含 migration/restore 测试库，再按确切路径清理；原83点及所有旧行保持不变。

## 已修复的验收错误

1. 早期脚本安装完就读 schema，应用还没启动执行 SQLiteOpenHelper 迁移；改为先真实启动再检查，原48点一直保留。
2. 差精度25米、移动14米的测试点先被录制器防漂移阈值过滤；增加模拟位移和等待，使其进入真实 points，再验证道路层拒绝。没有放宽产品过滤规则。
3. MuMu instrumentation / task 重建重置竖屏，曾得到黑截图但 DOM 有内容；不能将其视作 UI 验收。改为启动后重新锁定竖屏、明确“查看地图”聚焦公开道路，并在实际地图区域检查道路像素；最终两张有效截图均已人工查看。
4. Android instrumentation finish() 先结束进程，留下测试专用 cache；修正清理顺序并复验，只清理已经核对的本轮测试路径，产品 APK 不变。
5. OSM 接口初始环境代理连接被拒绝，改用无代理请求固定公开测试区，未读取凭据或发送私人区域；早期测试 way 查询不是道路，未进入道路包。

## 限制与回退

真实手机户外、vivo 后台/耗电、长期 Doze/重启、多年数据量、高密度复杂道路/驾车、Android14/16 运行及有 Key 的高德尚未验收。只提供小区域合法道路包，不提供自动城市下载，不保证路口全量匹配。用户手机未更新，不能称该版本已在其手机生效。

仅覆盖升级得到验证。旧 schema1 App 不能直接打开 schema2，**不要用卸载、清空或直接降级 APK 来回退**。保留旧源码、旧 APK、原始 trips/points；需要回退时另行设计并验证 schema2 兼容版本。没有推送、合并或对外发布。下一阶段若用户要求真机验证，应先获得相应授权并保留本机数据，不能沿用模拟器结论冒充真机通过。

---

# HANDOFF — v0.4 本地质量收尾（2026-10-09，Asia/Shanghai）

> 用户已明确恢复开发并要求本地收尾；本节取代下方旧“暂停/只讨论”指令。唯一写手、工作目录 `D:\Projects\WalkPaint`、分支 `feat/v04-reliable-roadpaint`，基线 `d5b63c65fb0e82c6393cf3df9f94ddb9afcb6213`。本轮不 push、不 merge、不发 Release、不共享 APK、不增加服务或依赖。最终提交 SHA 以该分支 `git log -1` 为准。

## 实现边界

- 持续位置前台服务请求 GPS（2 秒请求间隔，不保证系统实际回调间隔），取消旧 idle 切换和重复 passive 回调；GPS 注册失败、关闭/恢复与无回调限频重注册均可诊断。保留定位过滤门槛，拒绝精度 0/不可靠精度，断档不连成假连续线。
- **已删除持续续期 PARTIAL_WAKE_LOCK 逻辑，App 不长期持有 CPU 唤醒锁。** GPS 请求依赖 Android 定位前台服务；30 秒健康检查只在进程可运行时执行，不能绕过 Doze、OEM 杀后台或强制停止。
- `DiagnosticBuffer` 累计 GPS/入库/过滤/心跳，`RecordingDiagnostics` 在独立线程每 30 秒批量落盘；start/stop/failure/registration/gap 到来立即排队提交，同时保存未落盘计数。失败会重试并在 UI 标明。关键事件不会等待批次，但不保证突然杀进程前一定完成磁盘写入。末尾常规诊断可能丢约 30 秒，**SQLite 足迹保存不依赖诊断批次**。
- 诊断仅含固定原因、时间、次数、断档长度，不含经纬度、道路名或用户路线。我的页面可查看和复制；地图为细 GPS 线和小点，明确标注“道路涂色尚未接通”。
- `RoadPaint.kt` 已实现纯算法投影匹配、平行路/交叉口歧义拒绝、同路连续段约束、稳定路段 ID 的区间累计去重及曲线截取；测试 fixture 是固定合成 WGS84 数据，**不加载到真实足迹库**。没有生产道路数据适配器、道路覆盖持久库或 UI 集成，真正道路着色仍未交付。
- MapLibre/OpenFreeMap 当前视口曾查询到 121 个道路几何要素及 121 个瓦片 ID；这是只读诊断探测，**不能证明 ID 跨缩放/裁剪稳定，也不是已接通的道路匹配数据集**。没有合法高德 Android Key，没有报告高德底图成功；不盗用 Key/瓦片。

## 最终构建与升级

- JDK21（Eclipse Adoptium）、SDK36、AGP9.0.1、Gradle9.1.0；`gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --console=plain` 成功。**33 个 JVM 测试，失败 0、错误 0**，含 3 个诊断批次策略测试及 9 个道路算法测试。这不代表真实道路或 vivo 后台验收。
- 本地 APK：`app/build/outputs/apk/debug/app-debug.apk`，包名 `cn.walkpaint`、versionCode=5、versionName=0.4；SHA256 `468a7e3b17011dca9aac603bf4d8438efa92cd0ae1b33fae4ef81e6260ee4eed`。
- `apksigner verify --print-certs` 验证通过，证书 SHA256 `21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967`，与保留的 v0.3.1 universal APK 一致。SQLite schema=1、WGS84 历史格式不变；不卸载、不清数据、不迁移/重建真实足迹。

## 模拟器证据与缺口

- 仅操作允许的 MuMu `127.0.0.1:16384`，Android 15/API35；名称虽模拟 vivo，**不是 vivo 真机**。脚本 `scripts/android_v04_acceptance.py` 拒绝其他手机串号，用覆盖安装 `-r`，安装前后比较原 trips/points 的所有行，SQLite integrity_check 必须为 ok。
- **先前完整链路（质量收尾前 APK）通过**：授权返回自动启动、短途、静止漂移/差精度/零精度过滤、异常 GNSS 时间但单调时钟新鲜、HOME、熄屏、回前台、GPS 停供 50 秒恢复、>45 秒断档及分段、强制停止后手动重开、暂停无写入/再次同意恢复、历史保留。35→45 点，新增 10 个模拟点，原行全部保留。证据 `verification/v04-acceptance.log`、`verification/v04/acceptance.json`，cleanup_errors=[]；对应 APK SHA256 `9d8f6a90ac1e7784f751a87d2bc9845ea952d76d88645c1cd53144ddc26f8fc6`，**不是最终无唤醒锁/批次诊断 APK**。
- MuMu 曾断联导致一次 finally 清理报错；之后复连并恢复 MOCK_LOCATION=default，后续完整链路清理通过。最后短复核首次过早检查服务失败，服务实际稍后已进入前台；脚本增加有界等待，仍要求真实 dumpsys 证据，不以启动命令返回成功冒充服务运行。
- 最后版本只运行 `python scripts/android_v04_acceptance.py --quick --output verification/v04-final`，不重复长 GPS 链路；结果见下方最后复核记录。脚本 finally 独立尝试删除模拟 GPS provider、恢复 shell MOCK_LOCATION/定位开关/权限/旋转及临时 UI XML，并将清理失败写入 acceptance.json。
- **最后复核（2026-10-09 06:35 +08:00）通过**：`V04_FINAL_QUALITY_SMOKE_PASS`，同签名覆盖安装 code5；旧 45 点及所有原 trips/points 行保持不变；前台 45→46、HOME 46→47、熄屏 47→48；实际前台服务为 true、未持有 App CPU 唤醒锁、批次入库诊断已落盘。最终截图 `verification/v04-final/final-diagnostics.png` 已查看，48 点及启动/GPS/入库/过滤/断档字段真实可见。`verification/v04-final/acceptance.json` cleanup_errors=[]；复核 MOCK_LOCATION=default，系统日志确认移除 mock provider。短复核日志 `verification/v04-final-smoke.log`，构建日志 `verification/v04-final-gradle.log`。没有卸载、清数据或改写旧行，新增 3 点均为模拟器 GPS 测试。
- 实渲染截图在本地忽略提交目录：`verification/v04/map.png`、`history.png`、`profile.png`、`diagnostics*.png`；补充手机比例截图 `phone-map.png`/`phone-profile.png`/`phone-diagnostics.png`。最终诊断截图在 `verification/v04-final/`。这些是模拟器真实渲染和注入 GPS，不能当作用户户外实走。
- **未验收**：vivo 真机长时锁屏/后台/户外、实际耗电、长时间 Doze、重启自动恢复、Android14/API34 与 Android16/API36 实机运行、安全锁屏解锁、长期多日数据量；最终 APK 的完整停供/暂停链路未重跑。Android14+ 前台位置类型/权限已声明，后台自动启动还受背景定位权限和系统限制；强制停止后必须手动打开，OEM 电池策略无法保证永不断录。

## 恢复与数据保护

保留旧 APK 和用户数据库。ADB 不稳定时只重连同一允许的模拟器，核对 `appops get 2000 android:mock_location` 为原模式（本次 default），必要时在该模拟器移除测试 GPS provider；不要重启共享 ADB server 或操作用户手机。代码可从上述基线复查；旧版本同签名/schema 可作回退参考，但本轮没有验收降级安装，不能用卸载/清数据回退。`verification/`、APK、构建缓存、local.properties、签名文件与 Python cache 均忽略提交；测试数据库快照仅在进程内存比较，不输出原始坐标。

---

# 历史交接 — 新会话接手（2026-10-08 20:00，Asia/Shanghai）

> **最新用户要求：先停止开发、只讨论方案，准备新开会话接手。** 本节优先于下方历史交接。未经用户新的明确要求，不要继续开发 WalkPaint、安装新开发工具或再发 APK；更新交接文档不代表新版本完成。

## 一、真实目标与已确认选择

- **产品目标**：Android 私人足迹涂色；一次主动同意并授予所需权限后，日常走路/出门自动记录，回看时真正走过的**道路段连续上色且可累计探索**，不是画 GPS 圆点串线。希望有清楚状态、每天的完整足迹、可靠备份；优先少维护、无需频繁手动启动。
- **最新关键授权选择**：用户**接受常驻通知和适当增加电耗**，优先提高长时间后台记录完整性，但这不是允许规避安卓系统权限和限制；首次明确告知、用户可随时暂停仍为底线。
- **地图优先级**：高德 → 腾讯 → 百度。上游官方 SDK/API 与地图服务许可优先，不用无授权瓦片；地图与 GPS 数据层/道路匹配层解耦。
- **尚未确定**：步行、骑行、驾车是否统一涂色或分类统计。前轮助手建议分模式，但用户未作最终选择；下次只有确实影响实现时再询问。
- **实际体验反馈比先前测试结论优先**：用户 2026-10-08 19:37 提供真实手机两张截图，地图显示 **749 米 / 36 点 / 5 格**，可视结果只有几个巨大的青绿色圆点及短连线、底图大面积空白；“我的足迹”同一天分成 35 点/0.75 km 和 1 点/0.00 km 两条。用户明确反馈**放后台后记录自行中断**、涂色不好看、不符合“一次启动后日常无感”的目标。以上不证明中断根因是服务被杀还是 GPS 停采，应以真机日志和权限状态排查，不能凭截图推断。
- **质量评价**：用户认为 v0.2–v0.3.1 的 Vibe Coding 效率、质量均不合格；“构建通过 / 模拟器点数增长 / GitHub Release 创建”≠ 真实可用交付，不要把下次修一个阈值或换 UI 就称为解决。

## 二、当前可验证工程状态（只读核验于 2026-10-08 19:59 +08:00）

- 唯一工作目录 `D:\Projects\WalkPaint`；私有仓库 `h516df/walkpaint`；`main` 在核验时为 `965df7bd0b550a2b655a5ec7be1b66a44b78abc7`，与 `origin/main` 同步，工作树干净。此交接文档提交后需以新的 Git HEAD 为准。
- 最新 APK 是 GitHub [v0.3.1 私有预发布](https://github.com/h516df/walkpaint/releases/tag/v0.3.1)，本机 `WalkPaint-v0.3.1-arm64-debug.apk` 约 70 MB；旧 v0.3/v0.2 可作恢复参考，但**不要卸载或清空应用数据**。
- 应用包名 `cn.walkpaint`，v0.3.1 `versionCode=4`、历史调试签名、SQLite 本地轨迹持久化。高德 SDK 已在代码里，但**此版 APK 没有可用的高德 Android Key**，运行时仍为 MapLibre/OpenFreeMap；道路吸附/按路段永久去重、真实城市道路覆盖率尚未实现，腾讯/百度 SDK 未接入。
- 此前 18 项 JVM 单测通过、MuMu 安卓 15 用 `scripts/android_emulator_smoke.py` 模拟短途/漂移/异常时间戳均通过；**均不能证明 vivo 等真实手机锁屏、后台、省电、重启和长期定位稳定**。此前的演示点是模拟定位，不可当用户实走里程。
- 仓库根目录目前**没有 `.github/workflows` 和 `app/src/androidTest`**，没有形成持续集成的独立运行验收。截图证据在本机忽略提交的 `verification/`，请注意隐私不要推送真实轨迹。

## 三、本会话已讨论的根因与推荐方向（尚非实施结果）

- 先完成**持续记录可靠性**：分析前台位置服务、权限、国产手机电池白名单、GPS 实际更新/入库和异常恢复；允许常驻通知和适度耗电。用户不可接受只显示“服务运行中”却实际漏记。
- 再完成**按道路涂色**：不用大圆点代表已探索道路；先验证地图匹配/道路吸附、路段去重、断点处理及坐标体系，视觉上直接高亮走过的道路。仅换高德底图不能自动解决道路匹配。
- 数据展现应按天汇总、解释断录/暂停、统计和可视化一致；后续再做设备外自动备份、长期性能及其他探索趣味功能。
- 评估成熟路线而非补丁堆叠：后台定位研究 [GPSLogger](https://github.com/mendhak/gpslogger) / [OwnTracks Android](https://github.com/owntracks/android)；道路匹配参考 [Valhalla](https://github.com/valhalla/valhalla) / [GraphHopper](https://github.com/graphhopper/graphhopper)。开放源码或 GitHub 上可见不代表可以直接拷贝到本项目，需核对许可、数据、隐私、成本和离线/国内可用性。
- 用户**接受一次开启后的持续通知与电池开销**；但若长期后台记录在 Android 厂商策略下无法保证，需如实解释可达到的等级与失败补救，不能承诺无条件永不断录。

## 四、Vibe Coding 效率/质量复盘与 GitHub 工具筛选

- 采用**ChatGPT 作为任务入口/验收者 + 一个主写手（现有 Codex 合适时优先复用连续会话）**；不要多个 Agent/工具轮流直接改一个仓库，也不要额外造总管/新常驻编排。
- **一句话交付**指助手自行恢复项目、选型、实现、测试和确认真实使用路径；不指“回复更短 / 更新提交更快 / 立刻发 APK”。首个可运行结果之前尽量少打扰用户；物理真机、专属登录、合规授权等确实离不开用户的步骤再一次性提出。
- 过去的浪费：大量碎片式 Remote Desktop Commander 读写、重复扫描和重复构建、Codex 会话中断后重复调研、无效截图（MuMu 多显示器曾截到桌面）、没有事先设计真实验收路径、为没有 Key 的高德提前引入大依赖、未排查后台断录就发布。
- **优先候选**：[Maestro](https://github.com/mobile-dev-inc/Maestro)（Android 真界面 UI/E2E 脚本、CLI 和已有 MCP）、[ReactiveCircus/android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner) + [gradle/actions](https://github.com/gradle/actions)（GitHub Actions 自动构建/独立 Android 测试）。尚未作为本轮工作安装或配置；先核对本机是否已有、兼容性与 Actions 私有仓库额度，再做**最小**试验。
- **按需参考而非全装**：[Superpowers](https://github.com/obra/superpowers) 的系统化排障/完成前验证两种方法；[Paparazzi](https://github.com/cashapp/paparazzi) 与 [Roborazzi](https://github.com/takahirom/roborazzi) 的 UI 截图回归（二选一）；[Spec Kit](https://github.com/github/spec-kit) 适用于真正大型独立项目，不应拖慢小修复。
- **真正的交付门槛**：真实用户路径（位置授权→后台/锁屏走路→回到前台看持续记录→正确道路可视化→历史保留）必须明确验收；必要时远程真机设备不可访问可作“未验收”而不能写“完成”。代码改动后若原问题无法复现/验证，应先保存诊断与阻塞。
- **4 个衡量指标**：从一句话需求到真正可用的总时间、实际使用成功率、返工次数、用户被迫介入次数。不能只统计模型调用时间/提交数量。

## 五、新会话最短恢复路径

1. **先读取** `D:\AI\START.md`，执行 `D:\AI\ai.ps1 -Action TaskContext -Query WalkPaint`，再读本文件、项目 README 和 Git 当前分支/工作树/远端；长期 AI 工作流可按需读 Notion《AI 项目总控｜长期外部记忆》。历史截图在这轮聊天里，新会话若无法直接查看，以上观察就是经用户截图得出的完整事实，不凭空脑补。
2. **当前用户只要求先讨论，不直接动代码、安装插件、上传 APK、修改配置或强推分支。** 下一会话应先总结“哪些问题值得从成熟项目复用，怎样在尽量少环节下做到真实可用”，给出有取舍的方案。
3. 待用户明确恢复开发后，先形成一个能够复现和验证后台断录的真实测试计划；只有证据足够才做根因修复，再验证真实道路匹配与可视效果；新增工具最多按缺口挑选，不全装。
4. 已有 v0.3.1 发布包和旧数据都应保留；任何新 APK 都必须可覆盖升级，禁止通过卸载清数据来取得虚假的测试通过。

---

## v0.3.1 历史交接（原始记录）

# HANDOFF — WalkPaint v0.3.1（2026-10-08）

**背景：** 用户明确反馈 v0.3 真机实际体验仍差，不能把 Gradle 构建和 GitHub Release 当作产品验收。这次只处理录制/短途展示/诊断核心缺陷，未增加新的常驻框架。

**修复与根因：**
- 之前高精度阈值按 GPS 误差 x1.25，短途可能被丢弃；v0.3.1 仅在 GPS 速度可信或连续三个定位呈合理同向运动时适当放宽，其余仍严格过滤静止漂移/跳点。
- 存库后里程又执行了一遍更严格的门槛，可能画出轨迹却显示 0 米；现在统一里程统计已存合法路段，短距离页面显示米数。
- 之前缺少 GPS 当前位置时地图回到上一次足迹的 zoom14.5，约 10~20 米的轨迹缩成几个像素，被圆点盖住；改用 zoom18 的街道级缩放、高对比度路线轮廓和首尾两个节点。竖屏真渲染已比对。
- 点击历史路线旧实现延迟 500ms，经常早于地图样式加载而无效；现在使用 pendingTripFocus，在地图就绪后再应用。
- 原先 GPS Wall-clock 异常直接丢弃；现在核验 Location.elapsedRealtimeNanos 小于30秒后才按当前时间修复，并阻止旧定位/精度0假定位；UI不把前台服务已启动误称为已保存足迹。
- “我的”增加复制**不包含经纬度/路线**的诊断摘要，便于后续定位真机问题；减少无意义地图 SDK 开发说明。定位通知更新限频，避免每次 GPS 入库都刷新通知。

**可重复验证（本机）：**
- Android SDK36 / JDK21 / Gradle9.1.0：:app:testDebugUnitTest :app:assembleDebug 成功；共18个JVM单测，失败0。
- scripts/android_emulator_smoke.py（只允许 MuMu loopback 或 emulator- 串号）运行结果：ANDROID_SMOKE_PASS，3个模拟步行点新增、静止漂移新增0、GNSS时间错4分钟但monotonic新鲜的定位点新增1、foreground service运行；提供器和 Mock Location 权限已在 finally 中恢复。
- 安卓15 MuMu中实际截图和触控：portrait地图短路线显色、足迹列表→地图定位、我的→诊断入口；截屏在本机 verification/，此目录 Git 忽略。APK装回覆盖安装，应用包名 cn.walkpaint，versionCode4，沿用历史调试签名；旧SQLite未清理。
- 构建产物：D:\Projects\WalkPaint\WalkPaint-v0.3.1-arm64-debug.apk（约70MB）、同目录universal包；GitHub release是否发布须以当时远端核查为准。

**尚未解决 / 阻塞：** 没有用户高德官方Android Key，当前手机显示底图仍是OpenFreeMap；没有道路吸附、永久去重、真正城市道路覆盖率；用户真实安卓手机尚未接入电脑，无户外/锁屏/省电/多日与系统停止后的真机验收。若在用户手机仍不好用，优先从“我的→复制无位置坐标的诊断信息”收取最小证据再定位，不要盲目改阈值。不要把模拟器通过宣称为用户真机通过。

**回退与保护：** private v0.3 Release仍在；禁止卸载清空足迹，v0.3.1使用同包名同签名versionCode4升级；数据SQLite schema不变。项目相关改动一律核对Git/测试/最新Release。实际程序状态不从本交接文件猜。

---

## v0.3 历史记录



## 当前可交接状态

**用户本轮明确目标**：定位授权后应立即自动记录，不需要再次按开始；几步路有真实可见的上色反馈；“我的”改人物图标；国内地图优先高德、其次腾讯、再其次百度；长期迭代不能牺牲质量或数据。

**当前工作**：v0.3 核心位于 feat/v03-usable-footprints 分支（具体 main/Release 状态每次以实时 Git 为准）。v0.3 code3，应用包名 cn.walkpaint、debug 签名与 v0.1/v0.2 一致；SQLite schema version 未变，不能卸载旧 App 测试。

**已实施**：
- 首次明确告知“同意并开启自动足迹”，精确定位授权成功后自动启动 GPS foreground service；拒绝不偷录；已有 automatic_enabled 状态不重置。
- GPS 有效首点可见蓝绿色涂色种子，后续连续点画线；显示精度、最近入库时间、有效点总数和过滤原因。移动时提高采样频率，待机再降低；仍有 GPS/电池限制。
- 路线点数据库按时间索引获取最新点，地图快照/GeoJSON 异步刷新；新增 TrackMapJson 与单测；矢量导航图标，个人页面为人物图标。
- **高德官方 SDK**：依赖 com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1；独立 SDK 隐私同意后才初始化 AmapMapController，提供可打开的官方隐私政策链接；本地开发者 Key 从 Git 忽略的 local.properties 读取，缺少则 OpenFreeMap；数据库全部 WGS84，地图显示时转换到高德坐标，不改写历史。腾讯/百度未接入。

**验证证据（2026-10-08）**：
- Gradle :app:testDebugUnitTest :app:assembleDebug 通过；14 项 JVM 单测。
- MuMu Android15 ADB 覆盖安装成功；真实点击首次授权确认，检查系统服务 isForeground=true，显示常驻通知；已保存足迹点数原 3 → 6（三个模拟步行 GPS 点），地图蓝绿色点/线和统计变化。
- 测试定位提供器与 shell Mock Location 权限均已移除/恢复，未卸载应用或清除旧数据。截图在本机 verification/，该目录不提交 Git。
- arm64 包约 70 MB；universal 包约 125 MB；两种 APK 均沿用原测试签名，arm64 包签名 SHA1 FA:E3:DF:35:B3:2F:A7:DF:41:B3:F8:53:79:3E:A6:9D:39:5B:8E:52。最终 Release/提交状态需再次核验。

**外部阻塞**：当前还没有用户自己的高德 Android 开发者 Key，不能展示高德官方底图，更不能称其已真机验收。Key 需要绑定包名 cn.walkpaint 和签名 SHA1，设置见 docs/AMap_SETUP.md；Key 不应通过聊天公开。Android 真机户外、锁屏、重启、功耗、多日稳定性尚未验证，短路线仍可能因硬件定位精度被过滤。

**高优先级下一阶段**：1) 拿合法 Key 后实测高德底图并核对官方许可和隐私清单；2) 安卓真机至少多次短途+锁屏/重启/耗电验收；3) 道路吸附与重复去重、正确的城市道路覆盖口径；4) 授权后自动的设备外备份；5) 长期轨迹分段渲染和压缩，不为了架构先做过度抽象。若高德因账号/许可不可用，再按顺序评估腾讯和百度官方 SDK。

---

## v0.2 历史交接（已归档）

**用户目标：** 不用每次手动开启运动记录；在 Android 手机上长期自动积累生活足迹，城市地图上的已走道路逐渐上色；UI 现代、有个人统计和备份。高德是优先候选但目前没有官方 Key。

**Authority：** 私人 GitHub 仓库 `h516df/walkpaint`；本机 `D:\Projects\WalkPaint`；代码实际状态以 Git/Gradle/APK 为准。不要泄露 GPS 数据、密钥或签名私钥。

**用户体验边界：** 自动模式仍需要首次明确授权并打开开关。Android 需要可见的 foreground service 通知；待机调低定位频率，短路线可能遗漏；重启恢复需要 ACCESS_BACKGROUND_LOCATION 和操作系统允许，某些 OEM 会终止服务。不能保证“无条件永久运行”。

**模块：** `MainActivity.kt` + `Screens.kt`（Compose Material3 / MapLibre）；`RecordingService.kt`（Android location foreground service / retained preference）；`FootprintDatabase.kt`（SQLite / 旧版 JSON 迁移 / JSON 合并备份 / 本地周期快照）；`Tracks.kt`（v1 JSON 格式及 GPS 过滤）；`WalkPaintApp.kt`（仓库初始化）。

**升级保护：** `applicationId=cn.walkpaint`，v0.2 `versionCode=2`，使用和 v0.1 相同的本机 Android debug 签名证书。v0.1 `tracks.json` 导入 SQLite 时保留旧文件，失败 fail-closed 不覆盖。后续新版本需要显式数据库迁移，不要卸载旧 App 重装测试用户数据。

**验证完成：** Android 15 MuMu 模拟器安装 v0.1 → 放入 3 个 GPS 历史点 → 覆盖安装 v0.2 → SQLite 转移成功，界面能显示旧里程/天数；MapLibre 底图加载与“地图/足迹/我的”三页截图验收；DocumentsUI JSON 导出后重新导入不崩溃；自动定位 Foreground Service 在模拟器显示前台通知；10 个 JVM 单测。详细 screenshots 位于 `verification/`（本地忽略提交）。

**发布与安全复核（2026-10-08 18:00 +08:00）：** 私有 GitHub Release `https://github.com/h516df/walkpaint/releases/tag/v0.2` 已发布预览版 APK `WalkPaint-v0.2-debug.apk`（54,355,502 字节）；本机 APK SHA-256 为 `DA5B74B9BC60FFF6DE573117144BBE320C6C3DB0666A63BE5381AFD73C21BA3B`，`apksigner verify` 验证通过，SHA-1 与 v0.1 文档记录的 debug 签名匹配。使用 Gitleaks 8.30.1 对全部 2 次提交完成仓库扫描，退出码 0，未发现匹配的秘密。以上是发布与静态验证，**不是安卓真机长期定位验收**。

**未验证/需后续：** 安卓真机户外 GPS、频繁锁屏/杀后台、省电续航、长期数据量、设备重启自动恢复及云端自动备份；无高德 Android Key，未接 AMap；道路吸附/去重覆盖率/地名反查尚不存在；模拟器曾回报非真实年月的定位点，新代码已拒绝异常时间戳，但需在真机测证。

**下一版优先：**
1. 真实手机后台记录与通知/权限 UX，确认后台采样不会因多种省电策略中断；必要时让用户仅操作一次定位、电池设置。
2. 正规接入高德 SDK：用户高德开放平台 Android Key + 包名 `cn.walkpaint` + 签名 SHA1，隐私合规；参考 `docs/AMap_SETUP.md`。
3. 真实道路吸附，回收 GPS 漂移、道路重复累计，以及覆盖率/城市维度的正确口径。
4. 数据自动同步方案（用户授权的云盘 / 系统备份），确保可恢复且版本兼容；必要时 SQLite 长期压缩与分段地图渲染性能。

**运行环境：** 本机 SDK `D:\Apps\Android\sdk`，JDK21 `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`，Gradle wrapper `D:\Apps\Android\gradle-home\wrapper\dists\gradle-9.1.0-bin\9agqghryom9wkf8r80qlhnts3\gradle-9.1.0\bin\gradle.bat`，项目独立 GRADLE_USER_HOME 为 `D:\Projects\WalkPaint\.gradle-user-home`。不要操作与本项目无关的 Android 项目。初始 v0.1 Release 仍在私有 GitHub 可回退，v0.2 单独新 release 不能覆盖旧资产。
