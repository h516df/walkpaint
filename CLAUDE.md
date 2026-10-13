# v0.41 手机界面改版候选（2026-10-12）

用户已选定移动端设计并要求真正用到 Android App。当前任务 #35，工作区 D:\Workspaces\Codex\2026-10-12\jie\work\worktrees\WalkPaint\codex-mobile-ui-redesign-v041，分支 agent/codex/mobile-ui-redesign-v041，固定基线 v0.40/e4b8301。代码、参考设计与证据在本分支；PR 链接见 #35 的关联 PR。

候选 0.41/code43：原生地图摘要/记录操作、足迹列表/详情、我的、授权说明与深浅主题已完成。用户的最新选定设计取代历史“首页不得有摘要/记录按钮、我的只留两项”视觉约束；保留当前真实地图/迷雾、记录服务、原数据与颜色选择、原签名和更新安全流程。

状态：301 JVM、正式构建及专用 Google AVD UI/320dp/旧行保留 PASS；ARM64 正式候选已准备，执行 dex 与验收通用包一致。用户手机/高德 ARM/本人下载安装/长期户外未验收。证据、包指纹和发布边界见 docs/evidence/V041_MOBILE_UI.md；不自动合并旧 PR 栈，不把代码推送或候选准备当手机已更新。

后续：核对本分支 HEAD 和实时 Release，再按原私有渠道发布；用户只完成本人授权与系统安装确认。禁止覆盖既有版本或卸载/清数据；回退需原签名/schema2/code>=43 修复包。

---

# 项目规则

通用规则来源：`D:\Projects\agent-rules\AGENTS.md`。当前事实以实时 Git、GitHub Release 和本项目 HANDOFF 最新节为准。

## 当前目标与入口

- 本轮用户明确要求 Codex 接手，优先修复自动记录中断、走过路线不上色，并完善应用内更新；已授权做好后发布到原私有仓库。DSH 对应任务已取消，原 DSH 工作区的未提交修改保持原样。
- 当前版本 0.38 / code40；从已发布 v0.37 的 DSH 分支提交 5059f979857afedfe206accdea263cf18322e18f 继续。main 仍旧，不能从旧 main 重做，也不自动合并历史 PR 栈。
- 用户要连续、柔和的探索迷雾，沿已保存 GPS 路线显露地面；不要恢复方格、全屏网格染色或默认并排轨迹线。无需下载道路包。日期回看保留已有行为。
- 地图/足迹/我的沿用现有入口；“定位我”应回到当前位置街道视野，“全部探索”才显示历史范围。自动记录、检查更新在我的页。

## 数据、定位与联网

- 包名 cn.walkpaint、schema2、原签名以及全部 trips/points/roads/road_matches/coverage 保留。覆盖升级，不卸载、清库或降级。回退须同签名、schema2、versionCode 大于等于40的修复包。
- 只修有证据的缺陷；不猜测 vivo 中断根因，不放宽定位过滤，不加常驻唤醒锁、遥测、第二个服务或自动上传。GPS 保存和地图显示独立于道路匹配。
- 服务运行不等于保存：区分回调、有效 GPS、保存、过滤和停止原因。GNSS 订阅不代表已收到定位。诊断仅已有本地白名单，不输出私人位置。
- 高德仅在显示边界做一次坐标转换；高德/备用地图需独立联网同意。合法高德配置在 Git 忽略的 local.properties，禁止读取、打印或提交值。
- 私有更新仅读取 h516df/walkpaint，安装包仅包含公开 client id；授权通过本人 GitHub 设备流程，本机 Keystore 加密，可退出清理。校验大小、SHA256、包名、版本、原签名和架构后交给系统确认安装。

## 构建、验证和发布

- 自动化只允许 emulator-5556，AVD 必须是 WalkPaint_Google_API36 且已启动；先调用 scripts/google_avd_guard.py。严禁自动操作个人 MuMu、其他模拟器或真机；Google AVD 的 MapLibre/OpenFreeMap 通过不能代表 vivo/ARM 高德通过。
- 禁止卸载、清库、重启共享 ADB、禁用共享网络；保留原权限/数据/provider/位置开关，清理仅本轮临时会话。验证原行保留，真实看截图和运行日志。
- 本轮已用原证书构建非调试正式包，缺少原签名文件时构建直接失败，不能生成新证书替代。两个版本代码和内置说明一致；ARM64 包不能冒充 x86_64 验收。
- 发布用 scripts/publish_release.py（适配 h516df/android-update-kit）：中文内置说明同源，arm64/universal 正式文件，SHA256 清单；兼容旧版本选择器的 -debug 名别名与正式包逐字节相同。绑定精确提交/tag，先上传草稿核对服务端摘要，再发布；禁止覆盖现有版本。
- 构建/下载/发布由 Agent 完成。用户只做必要的本人授权、系统安装确认和真实外出验证。无真机访问时明确未验收，Issue #6 保持开放。
- 本轮不合并历史 PR；业务改动在 agent/codex/recording-recovery-v038，独立目录 D:\Worktrees\WalkPaint\codex-recording-recovery-v038。证据见 docs/evidence/V038.md。
- verification/、dist/、APK、缓存、凭据和数据库快照不提交。只可提交已核对的无私人数据证据，绝不上传原轨迹或凭据。
