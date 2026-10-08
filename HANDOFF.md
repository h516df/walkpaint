# HANDOFF — WalkPaint v0.2（2026-10-08）

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
