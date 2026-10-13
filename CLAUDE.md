# v0.46：黑雾缩放延迟、切页与当天回看（2026-10-13）

最新用户纠正：放大缩小时黑雾有延迟，不要反复询问。目标与授权已明确，当前 Codex 单写手连续实现、验证和私有发布；保留 OpenDesign 视觉及底部探索/足迹/我的。

基线正式 v0.45 / 05f0f9acc3c541c643f2be640d02d30a31e38363。任务 agent/codex/map-history-performance-v046，Issue #47，目录 D:\Workspaces\Codex\2026-10-13\xai\work\worktrees\WalkPaint\codex-opendesign-zoom-v045；PR 必须整合 agent/codex/mobile-ui-redesign-v041，main 仍为旧基线；不恢复旧 DSH 写手/旧发布任务。

地图常驻；当天迷雾/位置/定位当天统一上下文；黑雾/路线/标记用已有原生 Android 覆盖层整帧绘制，实际底图/相机/投影仍为高德 SDK。手势和缩放动画按帧回调跟随投影，复用几何，取消高德完成图像后的600ms额外提交等待。不可变图像与场景代次避免缓冲重用和跨日异步结果；独立初始取景处理缓存命中后的冷启动；覆盖页面不消费子页面滚动，防止滑动误触。

0.46/code48，原签名/schema2；331 JVM、32 Python；同 ARM64 正式包的实际高德 JNI、7959点/140段匿名夹具、四次切页保持实例、当天上下文、25张过程画面/1044未知地理采样点、真实双指/连续按钮缩放、UI/冷启动/原五表数据保留通过。完整结果、产物摘要、源码指纹与限制见 docs/evidence/V046.md / v046/。生命周期 ready 时间与截图采样不能冒充完整 FPS。

GPS：本人报告后台正常，266秒无回调、最近240m精度、可见51/参与0；不能唯一诊断为厂商杀后台。持续零使用状态不再无限延后恢复，有限等待/限频重订阅，保持GPS与精度过滤；首页不把缺少新定位误报成后台停止，具体状态保留在我的/诊断。

只操作独占 emulator-5560 / WalkPaint_Google_API36_V044，真实加载高德；本人 vivo、户外长期GPS、FPS和本人OAuth/系统安装仍未验收，#6/#17开放。后续先核对手机实际版本和最新正式 Release，不提供低版本包。私有发布授权沿用；publisher固定SHA/草稿恢复/四资产摘要验证，不覆盖旧Release。回退同签名/schema2/code>=48修复包，禁止清数据。通用规则唯一来源本机 agent-rules，不向旧规则远端推送。

以下为历史记录，不替代本节当前要求。

---

# v0.45：底部导航、OpenDesign 视觉与快速缩放（2026-10-13）

用户最终纠正：“探索，足迹，我的应该放到下面的，不是在上面呀”。本节取代历史顶部导航要求。采用 OpenDesign 的冷雾白/深色、字体层次与轻量控件；三入口固定底部，地图控件上移避开底栏。保留完整地图、既有日历/统计/日期详情、我的记录设置/诊断/更新。

唯一主写手为当前 Codex，Issue #45，agent/codex/opendesign-zoom-v045，目录 D:\Workspaces\Codex\2026-10-13\xai\work\worktrees\WalkPaint\codex-opendesign-zoom-v045。从正式 v0.44 / 706dc98f2ed07aeef3a56818964bc9f40ca1be9a 隔离，不写旧会话目录，不整合历史 PR 栈。

已复现日期相机加载回调覆盖取景并修复；已复现旧版 45ms 间隔四次缩小 17→16（应13）；修复按请求目标累计、反向使用目标、保留相机中心、手势重置，切页不恢复跟随，定位按钮主动恢复。0.45/code47，原签名/schema2。327 JVM、29 Python、同包实际高德/日期相机/底栏/320dp主题/五表保留通过；生产包摘要见 docs/evidence/v045/，汇总见 docs/evidence/V045.md；不可用旧顶部布局的截图/包冒充本轮验收。

OpenDesign run 6c9f1231-8d7c-489b-ad9f-208a2702c27f 已 succeeded，七个文件完整，沿用 opencode/deepseek-v4.1-flash，官方默认参数；仅匿名文字和模拟数据，无真机截图/定位/私有源码外发。原设计顶部导航按用户后续纠正改为底部。官方仅静态/DOM shim；主写手另验真实浏览器和原生 Android。完整来源与映射见 docs/design/opendesign-v045/README.md。

测试独占 emulator-5560 / WalkPaint_Google_API36_V044，Android16、ARM64手机正式包经 native bridge 实际加载高德。此证据不等于本人 vivo；本人 GPS、长期户外/锁屏、本人 OAuth 下载/系统安装仍未验收，#6/#17 保持开放。不要称为手机已经更新或永不断录。原7行程/4865点、五表摘要必须保持。回退须同签名/schema2/code>=47修复包，不卸载/清库，不覆盖旧Release。

用户授权范围内连续完成私有推送、PR整合与版本发布；当前通用规则唯一源本机 D:\Projects\agent-rules\AGENTS.md，不再为规则推送 GitHub。既有业务发布入口 scripts/publish_release.py：固定提交、原草稿恢复、已上传资产只读核验、四资产齐全且服务器摘要相同后发布。传输问题 #39 不扩展本次 UI 范围。

下文为历史记录，不代表当前版本、布局、写手或待办。

---

# v0.44 首页与定位反馈交付（2026-10-13）

用户允许Codex决定布局：顶部探索/足迹，日历与统计合并；首页不放录制开关、定位状态、地图名称和重复足迹卡片。地图缩放过程中未知区域始终遮住。仅我的保留自动记录控制、诊断与更新。

唯一写手：本Codex会话；agent/codex/home-fog-gps-v044，D:\Worktrees\WalkPaint\codex-home-fog-gps-v044；Issue #42。基线为正式v0.43生产源码，接续4b99e60的相同生产代码/新交付工具。不写旧会话工作区，不合并历史PR栈。

0.44/code46、原签名/schema2；325JVM、29Python、同正式ARM64包实际高德与双指中间帧、日历/统计/详情/地图/我的/更新/重开、原行保留通过。详见docs/evidence/V044.md和v044/，发布以v0.44私有Release实时状态为准。

GPS修复仅为持续搜星不反复重注册、诊断口径与结论纠正。本人vivo/真实GPS/长期户外/本人账号下载与系统安装未验收，#6不关闭；不可称为永不断录或手机已经更新。后续先核对最新Release/本人手机版本，回退只能原签名/schema2/code>=46修复包，不卸载清库。

验收默认独占 emulator-5560 / WalkPaint_Google_API36_V044。原5556现有其他任务占用，不操作；守卫只允许固定名称与端口配对，拒绝其他设备。

以下是历史记录，不能以其中旧写手/暂停/版本覆盖本节。

---
# PR #38 验收工具整合（2026-10-13）

用户已明确允许解决整合冲突并清理相关旧脚本。本次只整合 a22f94e 的发布恢复、UI 行为断言和 AndroidTest 探针；生产源码、版本、签名与正式 v0.43 安装包不变。

正式 v0.43 标签固定 8a2d18097c7ce5b9cb00050457b202bc45d981f6，APK SHA256 为 b8aa5e7479a925e109dc2e544ee186c5acd7addfd575be6e2cf64c2a80e4c5a2；不可覆盖该发布。PR #38 只整合到 agent/codex/mobile-ui-redesign-v041，不连带合并历史 PR 栈。

旧 v0.40 发布/安装、纯 ARM 模拟器、自动手机授权和旧现场探针，以及误判 x86 高德的三个证据文件已按 SHA256 本地备份后移出当前入口。使用 amap_runtime_evidence.py / amap_ui_acceptance.py 验同一 ARM64 包和实际高德；发布沿用 publish_release.py，历史已发布版本仅 --verify-only。大文件上传传输改进仍由 Issue #39 跟踪。

以下交付工具证据是 v0.41 的历史 MapLibre 路径，不替代 v0.43 高德验收或本人手机验收。

---

# v0.43 高德同引擎验收（2026-10-13）

用户目标：解决 DSH 测试和 vivo 真机使用路径不同的问题；保留 v0.41 界面，未知区域全黑，只有走过的路线显露地图。

当前主写手是 Codex，分支 agent/codex/amap-v042，目录 D:\Workspaces\Codex\2026-10-13\xai\work\worktrees\WalkPaint\codex-amap-v042。DSH 两个会话及其已识别发布后台任务已中止；不要再次派发恢复旧写手。基线是已发布 v0.41/c46352e，保留诊断提交 d1500a2。历史 main 与 PR 栈不合并。

版本 0.43/code45、schema2、原签名。v0.42 标签已经由 DSH 推送到 13db588，未正式发布；保留该标签，不覆盖。正式验收只接受 ARM64 手机包在专用 Google AVD 经 native bridge 真实加载高德的路径，不能以备用地图通过代替。本人手机、长期户外记录及本人账号下载/系统安装仍未验收，Issue #6 保持开放。

本轮修复：宽帧不扩大三十米通道；首屏与空路线保持未知区域不透光；中国高德视口回到 WGS84 后再绘制；缩放时同步选择通道或路线线条。现场导出说明实际引擎/图层/像素。证据、测试与回退边界见 docs/evidence/V043.md。

以下保留的是历史交接记录；其中旧版本、写手与“x86 模拟器无法执行高德”的结论均不代表本轮状态。

---
# 历史任务：v0.41 交付工具与行为验收改进

用户要求提高经常进行的 Vibe Coding 效率与质量，并明确要求直接实施。唯一写手是当前 Codex；工作区 work/ops-worktrees/WalkPaint/codex-delivery-regression-tools，分支 agent/codex/delivery-regression-tools，基线为已发布 v0.41 的 c46352eb2f700b78abeeb4d2f30b8271c08f0d01。

本轮仅改发布工具、AndroidTest 探针和 UI 验收脚本；生产源码、版本、记录算法、原签名与正式安装包均保持。已发布 v0.41/code43，用户确认手机 v0.40，手机安装与 ARM 高德仍未验收。不自动整合历史 PR 栈。

发布模块复用 h516df/android-update-kit PR #6；恢复原草稿且只续传缺少资产，已发布版本只读核对。原发布入口新增 --verify-only；--no-legacy-alias 只在明确结束旧更新器兼容后使用，默认继续保留旧命名。

验证：16 项 Python 回归；AndroidTest 构建通过；已验收通用正式包不重建，专用 Google AVD 的实际日期集合、主题重启持久化、记录开关/服务、原五表旧行保留与安装包身份通过。实际引擎 OpenFreeMap/MapLibre。真实私有 v0.41 发布从原入口只读回读通过，零上传和发布写入。证据见 docs/evidence/DELIVERY_TOOLS.md。

后续接续先核对本分支 HEAD、PR 与 docs/evidence/delivery-tools/inputs.json；不因换会话重建同一包或重新发版。工具回退可还原本次工具提交，不卸载或清除用户数据库。普通 Android UI 测试只能用既有 emulator-5556/WalkPaint_Google_API36，必须先调用原设备守卫。

---

以下为前阶段记录：

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
