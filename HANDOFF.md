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
