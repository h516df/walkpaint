# 项目规则

- 当前任务已串行推进 #7/v0.10，工作区/基线/业务SHA/实际测试/回退见HANDOFF最新节；#6真机未完成，#8未开发。本节优先于旧暂停记录。位置/朝向跟随仅显示，禁止改录制、数据库、matcher门槛。手机旋转与长期耗电仍未测；用户已本次明确确认v0.10私有上传，服务器与重新下载校验通过，准确链接/标签/SHA见HANDOFF最新节。v0.9未另发布，旧规则限制是该历史状态。

- v0.9 私有Release/上传因自动审批审核要求本次明确确认而未执行；APK仅本地，不能提供假下载链接或换渠道上传。确认前保留候选、PR #9和旧v0.8。
- 当前 #6/v0.9 任务分支与工作区见 HANDOFF 当前节；从最新v0.8继续。健康检查异常恢复/本会话计数/GNSS无坐标证据/道路拒绝细分已通过94项JVM和MuMu全链，但vivo原故障未证实修好，#6不得关闭，#7/#8暂缓。原签名/schema2，code10；GNSS订阅不代表新GPS或入库，没有卫星回调不能解释成无卫星。


通用规则以 D:\Projects\agent-rules\AGENTS.md 为唯一来源。实时 Git、Issue/PR、最新 HANDOFF 和 APK 指纹优先于历史摘要。

- 当前用户契约是 v0.8 UI 改版：地图/真实道路是主角，首页不得放回大卡、录制开关、正常GPS状态、原始GPS串线/演示上色；操作和技术诊断进入我的。分支 feat/v08-map-first 来源于最新v0.7，未从旧 main 重做。
- 最新业务提交与准确 APK 见 HANDOFF 当前节。87项 JVM/Android真实UI短时验收通过；不拿这些替代vivo长期记录，不关闭Issue #2/#4。无定位的异步历史首屏需聚焦已有足迹，不能跳 SDK 默认北京；补截图实测回归。
- 每日道路UI缓存只重算有变化日期，道路目录换新时使缓存失效；不改SQLite、GPS服务或matcher阈值。道路原因诊断只读、无坐标、限最近2000段，并明确当前复算与历史确认不同。


- 用户已明确恢复开发，以 https://github.com/h516df/walkpaint/issues/2 的 P0 为历史v0.7契约。唯一写手，当时任务分支 issue-2-recording-health，来自核对过的 v0.6 分支 8bcddef；PR #1 / feat/v06-nearby-roads 未合并，main 不含 v0.4–v0.6。
- 本轮只修复有证据的软件缺陷并补持续记录、真实道路涂色的可观察闭环。真机原断录根因未确定，不猜测放宽阈值/加常驻唤醒锁，不扩大自动下载、云备份、移动分类或重型服务。
- cn.walkpaint、原签名、schema2 及全部旧 trips/points/道路/覆盖保持；覆盖升级，不卸载、清数据或降到不兼容schema1。v0.8候选code9，v0.6已发布资产保留。
- 前台服务运行不等于入库：回调、有效GPS、实际提交、过滤/失败/停止与系统状态分别显示。系统退出只读自身官方原因码，不读description/trace、不输出位置、不把未知退出归因vivo。
- 道路matcher与SAVEPOINT事务沿用v0.6；重复去重、歧义/差精度/断档/包外拒绝保持。当天显示只读已确认匹配，不能修改原行或跨trip/segment画连接线。
- 下载仅本次明确同意区域/IP后请求overpass-api.de；不自动上传位置、整城更新、重试换服务。联网底图同意独立，默认离线。
- Issue #4 已记载本机合法 Android Key 配置在 Git 忽略 local.properties；不得读取、打印或提交值。v0.8 高德实际模拟器渲染通过，vivo/中国道路对齐未测；SDK 初始化须独立同意，撤回可回退合法离线。GPS/OSM 为 WGS84，高德仅显示边界转换一次。
- Android 自动验收只允许 Google 官方 AVD：设备必须为 emulator-5556，且 AVD 名称必须是 WalkPaint_Google_API36、系统已启动。用户个人 MuMu (127.0.0.1:16384)、其他模拟器和真机严禁用于任何自动化测试、模拟位置、APK 安装。脚本先调用 scripts/google_avd_guard.py 检查真实设备身份；Google AVD 不可用时标记 BLOCKED，绝不降级到 MuMu。Google AVD 通过不代表用户 vivo 户外或长期后台已验收。 Google AVD 采用 x86_64，必须构建无 walkpaintArm64Only=true 的 universal debug APK；手机 arm64-only 预览包不能冒充 x86_64 模拟器验收。
- 禁止svc wifi/data disable、卸载、清库、重启共享ADB。只做已有恢复通道的模拟器测试，finally恢复provider/Mock Location/原权限/位置开关/旋转/临时文件并实读。
- 保存对应Git SHA/最终APK与已安装指纹、原行包含比较、截图与清理结果；verification/APK/凭据/私人GPS不提交或自动上传。catalogProbe只读目录与固定公开fixture，networkProbe默认关闭，禁止用真实GPS选网络测试区域。
- 历史v0.7的76项JVM、隔离库与P0 MuMu分项通过；整链重开曾FAIL并观测系统进程重启，原因未确定；同APK独立恢复专项PASS。不能称整链稳定通过。长时间Doze/厂商策略/重启/耗电/真实用户偏移仍未测；精确状态见HANDOFF，不能声称永不停止。
- 用户远程操作Codex；沿用此前明确私人仓库供下载的会话授权，现已提供v0.7私有测试下载：https://github.com/h516df/walkpaint/releases/tag/v0.7。不合并PR、不公开仓库、不上传私人轨迹；Issue #2保留开放直到真实验收。发布不等于手机已安装或后台修好。
- 电脑侧构建/检查/分发由Agent完成。用户只做手机安装确认、授权和实际外出，不默认USB到本机或要求执行命令/搬日志。没有手机访问能力时保留未验收，不能偷偷加遥测/远控/位置上传。
- 回退从schema2兼容源码构建同签名code>=9的修复包，不直接安装旧schema1版本。
