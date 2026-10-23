# v0.14 GPS 过滤诊断增强 — 本地验证

2026-10-10；基线 bcec979015593e67f8ae3567bcbce4bfec0f5f9b，目录 `D:\Worktrees\WalkPaint\codex-diagnostic-v014`，分支 `fix/v014-diagnostic-breakdown`。业务及此记录随本次提交保存；可由本地 Git 核对最终 SHA。无远端操作或其他写手。

## 已实现的入口与口径

- 我的 → 更多设置 → 高级诊断：既有四阶段计数之后显示独立的GPS过滤五类计数及历史窗口。
- 我的 → 更多设置 → 问题反馈 → 预览反馈报告：显示同一分类和窗口；沿用原主动预览/复制/系统分享，未新增自动发送。
- 过滤分组：STATIONARY_OR_SMALL_MOVE、JUMP_OR_TOO_FAST、MISSING_ACCURACY/UNRELIABLE_ACCURACY、STALE_FIX/NON_INCREASING_TIME，以及其他。INVALID_COORDINATE为其他；未知文本只保存 OTHER_FILTER，不保存原输入。每个 filtered 事件只增加一个分组和原过滤计数，valid_gps/saved不参与分类计算。
- 本次服务分类随 start 重置，随原快照重载；旧会话仅有过滤总数时显示分类缺口，不把它编造为其他原因。
- 最近30分钟/2小时只读原 ProblemHistory，按事件时间，包含窗口起点/当前时刻。整个桶在窗口内才计入整桶次数；跨起点的桶另列全部次数，其中窗口内数量未知，不比例分摊。保留桶都参与，线索展示最多30行不影响统计。
- 窗口无法识别行车时段；关闭收集、清空、上限裁剪、升级前缺失及旧其他原因不能倒推。无保留桶不能解释成零过滤或完整覆盖。
- 只增加五个有界字段和用户打开诊断时对最多500条线索的扫描；沿用原诊断线程、批量保存/重大事件即时保存。未修改 GPS 接收/过滤、道路匹配、阈值、SQLite、地图下载、上传或服务。
- 可选道路最近窗口复算本轮未做；既有当前目录最近最多2000段的只读复算保持。

## 实际运行结果

1. 新测试先运行：编译在新增 GpsFilterDiagnostics 引用处失败，证实基线不含此行为。初次构建环境还遇到只读缓存路径错误及公用缓存缺地图依赖；按报错改用现有项目依赖缓存，只读使用，未改其他工作目录。
2. 最终执行 `:app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain`：BUILD SUCCESSFUL，180 tests / 32 suites / 0 failures / 0 errors。新增9项（GpsFilterDiagnosticsTest 6项、FeedbackReportTest 3项）；171项既有测试也通过。
3. 新测试覆盖：1088/63/1025模拟计数场景的独立分类、所有白名单映射、服务重启/快照重载、旧会话缺口、精确窗口边界/跨窗口桶、30分钟/2小时、聚合重载、非过滤/未来/损坏桶排除、报告使用30行之外的保留桶、关闭与保存失败/空历史提示。
4. 脱敏测试注入坐标、原始速度、道路名、敏感ID、token样例和未知原因扩展文本，验证新缓存/历史/反馈自动信息不输出它们。用户自填描述仍按既有明确提示保留，不能宣称自动清洗了用户主动输入。
5. `python -m unittest discover -s scripts -p test_google_avd_guard.py`：7项通过。实际调用 require_walkpaint_google_avd 对 emulator-5556：失败，设备身份不可用；`adb devices -l` 为空。按照项目规则，未使用其他模拟器/真机，也未安装或改变任何设备。**真实诊断窗口、反馈滚动/渲染、Android16真机行车、长期后台和原涂色故障均未验收。** 没有清库、卸载、注入模拟轨迹或伪造截图。
6. `git diff --check` 通过。构建时Android metrics对全局 analytics.settings 的写入被系统拒绝，不影响测试/构建；原生SDK有既有符号不可剥离警告，按原库打包。

## 本地运行产物

`app/build/outputs/apk/debug/app-debug.apk`，通用debug APK，cn.walkpaint / 0.14 / code16，124880855 bytes。

SHA256：`dc4c5b20a20e5889220878e4fc65e520fbe5704080dcd6c560d616bdeedf91e0`。

构建/测试原始记录：本 worktree 的 `verification/diagnostic-build.log`、`verification/diagnostic-results.json`、`app/build/test-results/testDebugUnitTest/`、`app/build/reports/tests/testDebugUnitTest/index.html`（忽略提交）。APK仅本地，未安装或分发；本轮不改版本号、发布说明、PR或CHANGELOG。

复现最终构建（缓存只读，输出/Gradle缓存仅在此 worktree）：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:ANDROID_HOME='<android-sdk>'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle-user-home'
$env:GRADLE_RO_DEP_CACHE='<repo>\.gradle-user-home\caches'
& '<gradle-home>\wrapper\dists\<hash>\gradle-9.1.0\bin\gradle.bat' :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
```

回退仅本次源码差异，保留schema2/旧行/原签名；不卸载、不清空。用户2026-10-10真机反馈未在本机复现，分类增强用于下一次取得可复查线索，不代表根因已修复。
