# 项目规则

- 当前 #11/v0.14 软件更新闭环已通过，最新状态看 HANDOFF 首节与 docs/evidence/V014.md。171 JVM/7 guard、Google 真实私有授权/下载/取消重试/拒绝来源/取消安装/确认覆盖/原五表保留/新版检查/退出清理通过；系统 Done 导航修正后分段续验，不伪称整轮重跑。用户明确确认后私有预览开放，服务器与重新下载指纹一致，code16/0.14/schema2/原签名；草稿 PR #21 未合并，手机/vivo 户外长期后台/ARM 高德未验收。仅 walkpaint Contents/Metadata read App，无服务器/secret/private key/PC token；专用 Google 授权/Keystore/cache 已清，AVD 已停止。不动其他目录/MuMu/手机，不改记录/DB/matcher，不擅自关闭真机事项。此节优先以下历史。

- 当前串行完成 v0.13 的 #10 反馈/#11 浏览器更新MVP/#17 ARM兼容和#12继承整合；以HANDOFF首节和docs/evidence/V013.md为准。129 JVM/7守卫/Google真实反馈、覆盖升级、私有浏览器入口与50.04米真实GPS彩路通过；两包dex/内置说明一致。v0.13私有预览已分发并实际下载验SHA，PR未合并、手机未安装；已按本轮用户“做完再上传”授权统一私有分发，不单发v0.12。#11不冒充自动下载；vivo P0/长期后台与ARM高德本轮未验收。禁止改服务、DB、GPS/matcher阈值或测试私人MuMu。反馈仅固定白名单、本地有界留存、沿用诊断写入线程，不新增遥测/监控服务。回退原签名/schema2/code>=14。此节优先于以下历史状态。

- 当前串行推进 #12/v0.12，业务/候选包/测试与限制以 HANDOFF 首节及 docs/evidence/V012.md 为准。仅简化我的/权限引导/地图数据提示；禁止改记录服务、DB、GPS/matcher阈值。111 JVM、7 Google guard 和真实 Google UI/入库分段验收通过；Google 高德黑屏是已复现 v0.11 基线限制，底图/彩路像素未通过。APK 尚未发布，具体 v0.12 私有分发需用户确认；手机未安装，vivo P0 未解决，不关闭 #6/#7/#8/#12。不操作 MuMu/手机，后续只认 Google guard。回退同签名/schema2/code>=13。

- 当前已串行完成 #8/v0.11 颜色设置软件验收，当前分支/业务SHA/APK/证据以HANDOFF最新节为准。五预设/六位自定义/恢复默认，仅改变已探索道路笔画；设置来回须保留相机/历史/跟随。不改录制/DB/matcher/备份。103项JVM、两个真实SDK和MuMu实际UI/重复走路通过；真机尚未验收，#6/#7/#8保持开放。用户本轮明确确认后v0.11已私有分发，服务器与实际下载验SHA通过；链接及准确标签见HANDOFF。手机是否安装尚未核实，v0.10既有下载保留。本节优先于下方旧历史规则。


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
