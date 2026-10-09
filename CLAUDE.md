# 项目规则

通用规则以 D:\Projects\agent-rules\AGENTS.md 为唯一来源。实时 Git、Issue/PR、最新 HANDOFF 和 APK 指纹优先于历史摘要。

- 用户已明确恢复开发，以 https://github.com/h516df/walkpaint/issues/2 的 P0 为本轮契约。唯一写手，任务分支 issue-2-recording-health，来自核对过的 v0.6 分支 8bcddef；PR #1 / feat/v06-nearby-roads 未合并，main 不含 v0.4–v0.6。
- 本轮只修复有证据的软件缺陷并补持续记录、真实道路涂色的可观察闭环。真机原断录根因未确定，不猜测放宽阈值/加常驻唤醒锁，不扩大自动下载、云备份、移动分类或重型服务。
- cn.walkpaint、原签名、schema2 及全部旧 trips/points/道路/覆盖保持；覆盖升级，不卸载、清数据或降到不兼容schema1。v0.7候选code8，v0.6已发布资产保留。
- 前台服务运行不等于入库：回调、有效GPS、实际提交、过滤/失败/停止与系统状态分别显示。系统退出只读自身官方原因码，不读description/trace、不输出位置、不把未知退出归因vivo。
- 道路matcher与SAVEPOINT事务沿用v0.6；重复去重、歧义/差精度/断档/包外拒绝保持。当天显示只读已确认匹配，不能修改原行或跨trip/segment画连接线。
- 下载仅本次明确同意区域/IP后请求overpass-api.de；不自动上传位置、整城更新、重试换服务。联网底图同意独立，默认离线。
- 高德SDK已接入但没有合法Key；未验收高德底图，不能绕过官方Key/许可/独立同意。GPS/OSM为WGS84，高德仅显示边界转换。
- Android自动验收只允许127.0.0.1:16384 MuMu或emulator-*。MuMu回报V2284A也不是实际vivo；无真机时标BLOCKED，不能以模拟器/单测代替真机户外/长期后台。
- 禁止svc wifi/data disable、卸载、清库、重启共享ADB。只做已有恢复通道的模拟器测试，finally恢复provider/Mock Location/原权限/位置开关/旋转/临时文件并实读。
- 保存对应Git SHA/最终APK与已安装指纹、原行包含比较、截图与清理结果；verification/APK/凭据/私人GPS不提交或自动上传。catalogProbe只读目录与固定公开fixture，networkProbe默认关闭，禁止用真实GPS选网络测试区域。
- 当前76项JVM、隔离库与P0 MuMu分项通过；整链重开曾FAIL并观测系统进程重启，原因未确定；同APK独立恢复专项PASS。不能称整链稳定通过。长时间Doze/厂商策略/重启/耗电/真实用户偏移仍未测；精确状态见HANDOFF，不能声称永不停止。
- 按Issue #2要求不擅自合并、发布或上传私人轨迹。本轮新APK仅本地；可推独立分支并开依赖PR #1的草稿PR供复核，Issue #2保留开放直到真实验收。
- 回退从schema2兼容源码构建同签名code>=8的修复包，不直接安装旧schema1版本。
