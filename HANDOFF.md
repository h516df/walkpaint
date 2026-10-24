# 当前接续记录（2026-10-24 · v0.69 发行）

- **用户目标**：改进足迹涂色并发布可安装新版。沿用原 Android App，原包名 cn.walkpaint、原证书、SQLite schema3，覆盖升级保留资料。
- **分支 / PR**：`codex/walkpaint-v069-20261024` / [PR126](https://github.com/h516df/walkpaint/pull/126)，基线 main `ff6bddded601d3c640a176157ff3c5b6af0343fb`。已检查开放业务 PR；仅复用与本次发行直接相关的 PR124 固定签名准备代码，没有合并其他业务 PR。
- **实现**：月份统一联动日历、月/年统计和完整当月每日列表，月份可跳选；查找/收藏仍覆盖全部历史。当天详情通过系统文件选择器导出 GPX 1.1，精确位置导出前提示，保留 WGS84 原点、UTC 时间、断档分段，JSON 完整备份仍保留。0.69/code77。
- **代码验证提交**：`a3be66674e832e2330920ef9cf972683fb0c6588`。10项针对性检查 passed，完整541项 JVM/Native Graphics/Compose passed（0失败/错误/跳过），开发APK构建 passed；完整 `bash cloud/check.sh` passed，lint 0 errors（60 warnings/2 hints），后续 Python18+23 passed。独立 Python 18+23、签名策略7 passed；actionlint 1.7.7 passed。
- **原生界面**：已实际渲染、查看并操作历史换月/8天列表/查找/空月份恢复、保存状态恢复、320dp/1.5字体导出告知、真实 ContentResolver 写出 GPX；导出前后合成数据库备份一致。截图与来源见 `docs/acceptance/v0.69/`，不代表实体手机或真实高德联网。
- **发布路径**：签名材料仅在既有受保护 android-signing 环境中使用，不带出作业。等源码检查完成后整合 main，在该完整SHA手动运行 Original signing readiness，取同提交 signed-product 附件，按 docs/BUILD.md 使用现有发布脚本的公开/精确SHA模式，逐项校验包名、版本、架构、V1/V2/V3原签名和最终下载摘要。当前尚未发布0.69，不能把开发APK当正式升级包。
- **扫描**：通用 privacy_scan 已运行；本轮29个变更文本文件0命中。全库扫描保留的历史命中为旧脚本通用SDK路径、合成数值/URL、Kotlin标签与未提交.cloud配置，未将其重标成全库零命中。远端secret-scan单列检查。
- **未验**：实体手机覆盖安装、真实高德地图联网、户外GPS、锁屏耗电。本任务不操作私人设备。
- **下一步**：完成构建/CI与正式原签名作业，发布0.69并核验所有公开附件与latest，再更新此处实际发行结果；无需重新向用户确认已授权的发行。

---

# 当前接续记录（2026-10-21 · 统一通用入口）

- **目标**：适应用户经常新开云端 Codex 的方式；每个项目默认分支都指向同一通用工作流，并保留本地可执行简版，用户无需记启动语或分支名。
- **通用入口**：[vibe-ui-quality-kit/workflow](https://github.com/h516df/vibe-ui-quality-kit/blob/main/workflow/README.md)。简版修订 2026-10-21-r1；项目 AGENTS/CLAUDE 的特有约束继续适用。
- **本轮基线 / 分支**：`d7e86a06d0f2d3a711cdc276b246fc069f28834d` / `codex/shared-workflow-entry-20261021`。本轮只改规则与交接文档，不改业务源码、安装器、签名、数据、服务或发布权限。准确提交与 PR 以本次远端记录为准。
- **已完成**：统一入口、清理有效入口里的失效 agent-rules 引用、独立简版与自动发现相关任务的约定；保留下方项目历史和原检查入口。
- **验证**：本轮文档范围/简版一致性/项目约束保留/失效入口检查 passed；业务构建、设备、生产发布 not run。通用模板另在工具库验证。新 CI 结果以对应 PR 的实际运行记录为准。
- **当前任务发现**：下面是本轮开始前的未合并 PR 快照，不是建议合并清单。代理根据用户任务自行核对实时 head、依赖和相关分支交接；不要求用户逐个记住，也不能把最新 PR 自动当主线。
- **下一步**：新任务先读本仓库 AGENTS、云端说明与本记录，再按用户目标接续相关成果。完整私有规则暂不可读时先沿用本地简版；不能把存档视为账户级配置。

## 未合并任务快照（2026-10-21）

| PR | 任务分支 | 目标分支 | 读取时 head |
| --- | --- | --- | --- |
| [#111: fix: preserve update progress and safely continue installation](https://github.com/h516df/walkpaint/pull/111) | `dot/update-continuity-20261019` | `dot/fog-landmarks-20261019` | `8376c743c590eb6f7845030ad840153b467618d5` |
| [#110: feat: readable themed fog and locally saved exploration points](https://github.com/h516df/walkpaint/pull/110) | `dot/fog-landmarks-20261019` | `dot/fix-heading-follow-20261019` | `a058a5a3cf109733553784201b86cdfed6d4b5bd` |
| [#109: 修复位置/手机朝向跟随恢复，并解释首次探索黑雾](https://github.com/h516df/walkpaint/pull/109) | `dot/fix-heading-follow-20261019` | `main` | `1859a4892f3cb9bec7cbb1d5a1e96be9b5c71bb4` |
| [#104: 0.65：简化默认高德体验，加入足迹行记、收藏查找与路线明信片](https://github.com/h516df/walkpaint/pull/104) | `agent/first-run-amap-20261019` | `main` | `8b0f196a0d6961f5f08eb798ea53b38729f1fa44` |
| [#103: Native landscape UI preview: profile, original badges and adaptive navigation (v0.63-based)](https://github.com/h516df/walkpaint/pull/103) | `dot/native-landscape-ui-20261019` | `main` | `4a639563c385ed017a3af43eea82a98a01419150` |
| [#41: 高德可验收性 + 首屏迷雾根因修复 + 坐标基准（关联 #17，不用于再发布 0.43）](https://github.com/h516df/walkpaint/pull/41) | `dsh/v042-amap-verify` | `main` | `eef13c4b56a1be53ebe005fe6d12b956ac3ba21f` |
| [#36: v0.41：采用已选定设计重做手机原生界面](https://github.com/h516df/walkpaint/pull/36) | `agent/codex/mobile-ui-redesign-v041` | `dsh/v039-fog` | `d0a94af9d2e9a0b8390f2e7f7b91a326ca38b739` |
| [#34: v0.38：修复记录恢复、定位视野与正式包更新](https://github.com/h516df/walkpaint/pull/34) | `agent/codex/recording-recovery-v038` | `fix/dsh-lasterror-recovery` | `37af75fedc32c40bb0a13c9ad02fefaa0c6e66d2` |
| [#33: v0.32：连续软边探索迷雾与私有更新](https://github.com/h516df/walkpaint/pull/33) | `agent/codex/fog-of-war` | `fix/dsh-lasterror-recovery` | `5ae5dbc750b2f1f73f0cc93fed7d93219abfc6f4` |
| [#23: v0.16：高德联网地图直接涂色轨迹，无需准备地图](https://github.com/h516df/walkpaint/pull/23) | `agent/codex/direct-trails` | `agent/codex/diagnostic-acceptance` | `243510c6ae06b8f5bfe539dafad3a020793c5d31` |
| [#22: v0.15：GPS过滤分类、近期线索和实际诊断界面验收](https://github.com/h516df/walkpaint/pull/22) | `agent/codex/diagnostic-acceptance` | `agent/codex/issue11-private-updater` | `f202d8a13a29078f5b5b73781642b4231e89f9c2` |
| [#21: 补全私有更新授权、校验下载与系统覆盖安装](https://github.com/h516df/walkpaint/pull/21) | `agent/codex/issue11-private-updater` | `agent/codex/issue11-updates` | `dbd54adad73d5dddfa8a0d33d22b19e3ff1585a3` |
| [#20: feat: v0.13 中文更新说明与私有浏览器更新入口](https://github.com/h516df/walkpaint/pull/20) | `agent/codex/issue11-updates` | `agent/codex/issue10-feedback` | `ed6ee80f3d19334d6e59f04d92838b0c984a3a8d` |
| [#19: feat: 本地故障线索与预览分享反馈报告](https://github.com/h516df/walkpaint/pull/19) | `agent/codex/issue10-feedback` | `agent/codex/issue17-map-platform` | `38576168f6f9717d88942b16658338f0a93c0903` |
| [#18: fix: 不兼容高德的设备使用现有离线地图](https://github.com/h516df/walkpaint/pull/18) | `agent/codex/issue17-map-platform` | `agent/codex/issue12-simple-profile` | `82cc1c2af2371c663c1343e2a749f0327a6e0f92` |
| [#16: v0.12：简化我的页面与自动记录权限引导](https://github.com/h516df/walkpaint/pull/16) | `agent/codex/issue12-simple-profile` | `agent/codex/issue8-road-colors` | `c7bba2cb7992f4b6b6288cbd4f6087d7b4d37c2d` |
| [#15: v0.11：已走道路颜色自定义与本机保存](https://github.com/h516df/walkpaint/pull/15) | `agent/codex/issue8-road-colors` | `issue-7-map-follow` | `3f9604d034cf230021a566b40f0fb1acacc47420` |
| [#14: test: WalkPaint 测试只允许 Google 官方 AVD（保护个人 MuMu）](https://github.com/h516df/walkpaint/pull/14) | `chore/google-api36-test-only` | `issue-7-map-follow` | `35b6131c3ef9fbef66c10405518a8880ee482576` |
| [#13: P1: 地图跟随位置与手机朝向（v0.10 私有预览）](https://github.com/h516df/walkpaint/pull/13) | `issue-7-map-follow` | `issue-6-gps-road-diagnostics` | `0a5c6ada079b327f9948115146dfc8ad3477871d` |
| [#9: v0.9｜GPS 健康检查恢复与无坐标诊断（依赖 #5）](https://github.com/h516df/walkpaint/pull/9) | `issue-6-gps-road-diagnostics` | `feat/v08-map-first` | `5c3bcee1c3b057e09227c797405f1805b219409d` |
| [#5: [3/3] v0.8｜全屏高德地图与足迹/设置重排](https://github.com/h516df/walkpaint/pull/5) | `feat/v08-map-first` | `issue-2-recording-health` | `f987dc7fc6a047ed4ef450ca6a5523a0e1b4f3c5` |
| [#3: [2/3] v0.7｜GPS 记录状态与按天足迹](https://github.com/h516df/walkpaint/pull/3) | `issue-2-recording-health` | `feat/v06-nearby-roads` | `bffb1d4245235b269ebd41b97de7bf047657fda2` |

## 之前的交接（历史保留）

# 当前接续记录（2026-10-21）

本节是会话接续入口；长期规则见 [AGENTS.md](AGENTS.md)，工具链和检查命令见 [CLOUD_DEVELOPMENT.md](CLOUD_DEVELOPMENT.md)。后续任务更新本节，下面已有历史保留。

- **本轮目标**：将项目约定、进度和续接方式保存到 GitHub，使新 Codex 会话可以继续工作。
- **核对的主线基线**：`2c1af7dd8efb4aae7c4fb8744fde2c15c07f6c3b`（main 的日期快照，继续前重新读取；不是永远最新提交）。
- **本轮分支**：`codex/session-continuity-20261021`。此分支仅修改协作与交接文档，PR 和实际合并状态以 GitHub 为准。
- **已准备**：新会话阅读顺序、结束保存流程、远端提交核对及续接提示；保留原项目规则、既有历史和云端开发配置。
- **验证范围**：本轮检查文档差异、仓库内链接、原文保留及远端读回；业务测试/构建/设备/发布为 **not run**，历史证据不能重标成本轮结果。实际检查结果与远端提交见本轮 PR。
- **配置与边界**：地图 Key、原正式签名、真实 GPS、户外/锁屏耗电及覆盖升级继续按云端说明分别验收。 其他配置键名、初始化入口及限制仍以云端说明和任务相关文档为准。
- **下一步**：核对下面 PR 快照及当前 main；按用户的具体目标选择主线或指定分支，读对应 PR 的最新交接后继续。尚未完成的业务工作不因本轮文档完善而视作已完成。

## 打开的 PR 快照

以下仅为 2026-10-21 读取到的列表，不判断是否仍需实施、能否合并，或是否已由其他提交包含；不批量合并、关闭或重复搬运。

| PR | 分支 | 读取时 head | 状态 |
| --- | --- | --- | --- |
| [#111: fix: preserve update progress and safely continue installation](https://github.com/h516df/walkpaint/pull/111) | `dot/update-continuity-20261019` | `8376c743c590eb6f7845030ad840153b467618d5` | 草稿 |
| [#110: feat: readable themed fog and locally saved exploration points](https://github.com/h516df/walkpaint/pull/110) | `dot/fog-landmarks-20261019` | `a058a5a3cf109733553784201b86cdfed6d4b5bd` | 草稿 |
| [#109: 修复位置/手机朝向跟随恢复，并解释首次探索黑雾](https://github.com/h516df/walkpaint/pull/109) | `dot/fix-heading-follow-20261019` | `1859a4892f3cb9bec7cbb1d5a1e96be9b5c71bb4` | 草稿 |
| [#104: 0.65：简化默认高德体验，加入足迹行记、收藏查找与路线明信片](https://github.com/h516df/walkpaint/pull/104) | `agent/first-run-amap-20261019` | `8b0f196a0d6961f5f08eb798ea53b38729f1fa44` | 草稿 |
| [#103: Native landscape UI preview: profile, original badges and adaptive navigation (v0.63-based)](https://github.com/h516df/walkpaint/pull/103) | `dot/native-landscape-ui-20261019` | `4a639563c385ed017a3af43eea82a98a01419150` | 草稿 |
| [#41: 高德可验收性 + 首屏迷雾根因修复 + 坐标基准（关联 #17，不用于再发布 0.43）](https://github.com/h516df/walkpaint/pull/41) | `dsh/v042-amap-verify` | `eef13c4b56a1be53ebe005fe6d12b956ac3ba21f` | 打开 |
| [#36: v0.41：采用已选定设计重做手机原生界面](https://github.com/h516df/walkpaint/pull/36) | `agent/codex/mobile-ui-redesign-v041` | `d0a94af9d2e9a0b8390f2e7f7b91a326ca38b739` | 打开 |
| [#34: v0.38：修复记录恢复、定位视野与正式包更新](https://github.com/h516df/walkpaint/pull/34) | `agent/codex/recording-recovery-v038` | `37af75fedc32c40bb0a13c9ad02fefaa0c6e66d2` | 打开 |
| [#33: v0.32：连续软边探索迷雾与私有更新](https://github.com/h516df/walkpaint/pull/33) | `agent/codex/fog-of-war` | `5ae5dbc750b2f1f73f0cc93fed7d93219abfc6f4` | 草稿 |
| [#23: v0.16：高德联网地图直接涂色轨迹，无需准备地图](https://github.com/h516df/walkpaint/pull/23) | `agent/codex/direct-trails` | `243510c6ae06b8f5bfe539dafad3a020793c5d31` | 草稿 |
| [#22: v0.15：GPS过滤分类、近期线索和实际诊断界面验收](https://github.com/h516df/walkpaint/pull/22) | `agent/codex/diagnostic-acceptance` | `f202d8a13a29078f5b5b73781642b4231e89f9c2` | 草稿 |
| [#21: 补全私有更新授权、校验下载与系统覆盖安装](https://github.com/h516df/walkpaint/pull/21) | `agent/codex/issue11-private-updater` | `dbd54adad73d5dddfa8a0d33d22b19e3ff1585a3` | 草稿 |
| [#20: feat: v0.13 中文更新说明与私有浏览器更新入口](https://github.com/h516df/walkpaint/pull/20) | `agent/codex/issue11-updates` | `ed6ee80f3d19334d6e59f04d92838b0c984a3a8d` | 草稿 |
| [#19: feat: 本地故障线索与预览分享反馈报告](https://github.com/h516df/walkpaint/pull/19) | `agent/codex/issue10-feedback` | `38576168f6f9717d88942b16658338f0a93c0903` | 草稿 |
| [#18: fix: 不兼容高德的设备使用现有离线地图](https://github.com/h516df/walkpaint/pull/18) | `agent/codex/issue17-map-platform` | `82cc1c2af2371c663c1343e2a749f0327a6e0f92` | 草稿 |
| [#16: v0.12：简化我的页面与自动记录权限引导](https://github.com/h516df/walkpaint/pull/16) | `agent/codex/issue12-simple-profile` | `c7bba2cb7992f4b6b6288cbd4f6087d7b4d37c2d` | 草稿 |
| [#15: v0.11：已走道路颜色自定义与本机保存](https://github.com/h516df/walkpaint/pull/15) | `agent/codex/issue8-road-colors` | `3f9604d034cf230021a566b40f0fb1acacc47420` | 草稿 |
| [#14: test: WalkPaint 测试只允许 Google 官方 AVD（保护个人 MuMu）](https://github.com/h516df/walkpaint/pull/14) | `chore/google-api36-test-only` | `35b6131c3ef9fbef66c10405518a8880ee482576` | 草稿 |
| [#13: P1: 地图跟随位置与手机朝向（v0.10 私有预览）](https://github.com/h516df/walkpaint/pull/13) | `issue-7-map-follow` | `0a5c6ada079b327f9948115146dfc8ad3477871d` | 草稿 |
| [#9: v0.9｜GPS 健康检查恢复与无坐标诊断（依赖 #5）](https://github.com/h516df/walkpaint/pull/9) | `issue-6-gps-road-diagnostics` | `5c3bcee1c3b057e09227c797405f1805b219409d` | 草稿 |
| [#5: [3/3] v0.8｜全屏高德地图与足迹/设置重排](https://github.com/h516df/walkpaint/pull/5) | `feat/v08-map-first` | `f987dc7fc6a047ed4ef450ca6a5523a0e1b4f3c5` | 草稿 |
| [#3: [2/3] v0.7｜GPS 记录状态与按天足迹](https://github.com/h516df/walkpaint/pull/3) | `issue-2-recording-health` | `bffb1d4245235b269ebd41b97de7bf047657fda2` | 草稿 |

## 下一次交接时更新

请在本节写清本轮目标、完成/待办、工作分支和 PR、所验证的提交 SHA、实际命令与 passed / failed / not run、阻塞、下一步；代码和交接一起推送，再给用户已核实的远端提交链接。未合并时须交代指定分支，不能让新会话只读 main。

---

## 原有交接历史

以下原文保留；其中旧路径、候选版本与测试结论需按日期和源码重新核对。

# 0.57 开雾与更新

本轮修复已接受首点/短段未开雾，增加空历史定位状态，官方发布文件回退及有限续传。完整范围、正式 APK 摘要、409 JVM/23 Python、原生渲染/坐标/匿名下载及限制见 docs/evidence/V057.md。只读专用模拟器验收不能代替用户或女朋友手机，不能将 map_loaded 当作所有瓦片已加载。原签名、schema3、GPS 阈值和30米带宽保持。

## 0.55 隐私修复

Issue87：自动诊断导出限于独立开发调试包，官方包拒绝并清理旧诊断文件；原生验收改走同签名 androidTest，不重新开放生产出口。0.55/code63/schema3，沿用原签名。

395 JVM/41 Python、正式构建/lint、原生自动导出拒绝与旧诊断清理、高德运行检查通过；11行程/4905点及五表摘要保持。详见 docs/evidence/V055.md。仓库扫描/推送保护与依赖告警已开启；最终 PR、CI 和发布读回继续绑定本次提交。实体手机仍未验收。

PR88已合并，v0.55绑定1a250bfc7d5a9d76257c62150e714f406862d624正式发布。草稿恢复任务38942885381成功，APK大小/摘要保持原验收包；发布说明来自同一提交，Linux/Windows换行字节不同但JSON一致。旧发布未覆盖，细节见V055。

## 0.54 公开交付记录

原仓库h516df/walkpaint已公开，ID和地址保持，没有新建第二套私有仓库。GPL-3.0及高德SDK链接附加许可，主题插画来源已核对。PR84 squash到d0a94af9d2e9a0b8390f2e7f7b91a326ca38b739，main已顺序推进到该源树，v0.54/code62/schema3绑定该提交发布。

本轮：审查分支/标签/PR引用、图片和文本、Issue/评论、发布及Actions；原始审查与恢复副本只留本地。更新器改为匿名读取固定公开仓库和最新稳定发布，清除旧授权入口保留，安装校验和正式签名保持。增加独立开发构建，整理公开文档。

390 JVM/41 Python、正式构建/lint、真实系统覆盖和原五表/偏好保留通过。APK66,431,034 bytes，SHA256 f7d6e99d5599fd8e5853c07459cf3431a24cb702c2aea914c948eca83a361b6d，原证书V1/V2/V3。详见docs/evidence/V054.md和发布回执。60/61仅本机候选，未发布。

匿名仓库身份/main/GPL许可读取通过；公开浏览器实际下载的三个文件与最终资产大小/摘要一致。模拟器直连DNS失败，临时代理出口匿名API限额为0，App正确显示受限，目录/下载探针尚未通过。公开浏览器入口与App匿名API分开验收，不用认证请求冒充成功；限制见Issue85。

未改GPS/迷雾算法或数据库结构；实体手机未连接，安装、户外、锁屏及缩放流畅度不作通过声明。回退须原签名/schema3/code不降低，不覆盖旧发布、不卸载清库。临时模拟器网络及进程在本轮结束恢复/停止，保留原数据。


## 2026-10-18 用户纠正后的继续任务（实施中）

正式版仅高德地图；配置随官方包提供，不让普通用户注册开发者；有效位置点周围50米半径揭雾，历史点同样显示；保持GPS过滤、坐标和数据库原件。

同一写手分支 agent/codex/modern-experience-20261018；上一版本不覆盖。通过针对性逻辑和原生交互回归后再发新版本，实体手机未连接，不冒称真机已验。

当前待发布版本 0.58，证据见 docs/acceptance/v0.58/README.md。用户2026-10-18已授权既有公开/私有仓库连续推送、PR、审查合并及验后发版。

## 2026-10-18 成长与探索扩展
用户要求三个App增加游戏化、记录感和成就感；足迹收拢重复诊断入口，探索页显示可核验的地区探索度，并提供后台稳定设置与最近任务隐藏选项。当前Codex单写手，沿用独立工作区；本轮交付成长页、等级/徽章/周目标/活跃日历、本机初次安装与前台使用统计。奖励绑定真实记录，升级保留旧数据；未得到村级边界不能伪称村级百分比，不承诺系统强制停止后永不退出。真实UI、持久化与原有功能回归后发布递增版本。

2026-10-18 用户新增审美约束：苹果式克制、留白、少文字，不铺满。成长页改为稀疏数字布局，徽章墙与日历按需展开，统计规则收进说明。

0.59/code67已完成418 JVM、23 Python、正式高德运行/坐标对齐、成长持久化、地区附近统计及25帧迷雾回归。详见docs/acceptance/v0.59/README.md；区县网络、完整底图和实体手机路径未验收。保持足迹涂色名称与图标。

第四轮v0.60按用户的少字、小白友好与原神质感方向调整。证据见docs/acceptance/v0.60，未改已有数据格式。


## Issue97 · 0.61 界面改进（2026-10-18）

用户认为前版仅有苹果/原神的表面装饰，要地图探索、克制层级与自然展开，不要统计报表。当前独立分支 agent/codex/issue97-map-journey，基线 v0.60 的752edf1。地图浮层改成轻量罗盘入口，详情显示真实点亮面积、附近2公里或取得的真实区县范围；说明按需展开。探索护照以已保存路线与纪念徽章为内容，周目标、使用记录折叠。我的集中外观与后台设置，足迹优先日历与真实路线。

未改地图引擎、揭雾/百分比算法、GPS过滤、数据库或签名；保持数据与更新校验。0.61/code69，正式构建、原生路径与屏幕验收待执行，不能把源码变更当作已验收。由root统一审查、PR/合并及发布，不操作v0.59空草稿。区县网络、完整道路底图和实体手机路径仍未验收。

0.61候选验收完成：420 JVM、41 Python，5588正式AMap运行、Growth 1.0/1.3字体、Region13.7%、25帧未知区全黑1.0。稳定截图与产品/产物指纹见 docs/acceptance/v0.61。后续仅root复核、合并、发布及资产读回；实体手机/完整底图/区县网络限制保持。


## Issue100 · 0.62 地区行记与后台隐藏（2026-10-19）
用户明确：后台隐藏是最近应用列表消失但桌面仍可打开，默认关；成就不能开车一天完成，需要县/市/省长期探索，拒绝“探索护照”“渐渐熟悉”等空泛命名。独立实现分支 issue-regional-achievements-v062，基线 f1dfeea，版本0.62/code70。研究与决策见 docs/design/issue100-regional-journey.md。
按官方高德省→市→县层级识别真实记录样本；初临/寻迹/深游/长游区分首次、7/30/100天和真实下级地区数，面积另按取得的县界计算，附近2km不用于行政区授奖。旧轨迹只读回算、同区同日去重，派生索引可重建，原SQLite/GPS/雾算法/签名不动。直辖市及省直管县只认返回层级，不猜父级代码；解析失败保留未知。
实现、针对性逻辑与原生验收进行中；root统一协调构建/AVD、复核PR及发布。旧限制（实体手机、完整底图）继续如实保留，不把夹具当联网证据。

0.62候选交付：431 JVM、41 Python，最终APK cd18e245baecfcf1fa751c8cae3e2a70ec6bb7019798e0ec5ed93441cf663aa5。真实高德东城区593顶点县界通过，修复同步成功异常字段null误判；最终包后台隐藏新增9点、桌面重开、五表覆盖安装摘要保持通过。大字面板实滑全徽章可达，探针仅末尾清理导航失败已明确记录。完整证据及源码指纹见docs/acceptance/v0.62；完整道路底图与实体手机仍未验收。root复核合并发布，不覆盖旧版本。

## 2026-10-19 首次体验简化（候选，未发布）

用户要求默认高德地图、开箱即用、进入后同意即可体验，减少普通用户的配置步骤。本轮基于 main bfb27a1，在 agent/first-run-amap-20261019 实施：首次引导中明确地图告知和后台记录用途，一次“同意并开始”后只申请精确定位，沿用原键保存高德同意；当前可见 Activity 中启动原定位前台服务，后台权限增强留到设置中按需开启。已拒绝地图的用户从自动记录开关进入时仍可单独同意记录，不替其同意地图。

主按钮固定在底部，小屏与大字体的说明区可滚动；暂不开始不再接着弹地图同意框，系统拒绝定位不再立即叠加设置弹框。空足迹地图上的记录提示直接进入处理流程；缺少可选后台权限不再遮盖正常状态或真正的 GPS 故障。正式版不再初始化备用 MapLibre，官方调试/正式 APK 及 Bundle 打包补地图 Key 门禁；普通用户无需配置 Key，开发包仍独立 cn.walkpaint.dev。

未改 GPS 过滤、50 米揭雾、原始足迹、schema3 或正式签名。当前环境无原签名、高德 Key、设备或模拟器，不能声称高德底图、系统权限实操、首点开雾、小屏/大字体、锁屏或覆盖安装已验收，也未发布新版本。验证：源码 f4bdde2 上 11 项首次启动针对性 JVM 测试、433 项完整 JVM 测试（0 失败/错误/跳过）、41 项 Python 检查、两个更新后验收脚本的语法检查与独立开发包 assembleDebug 通过。使用 Android SDK36、完整 JDK21、环境代理及现有 Java CA 信任库，未关闭 TLS 校验。GitHub 草稿 PR #104 的交付工具与密钥扫描通过；不把这些检查当作原生 UI 或正式地图验收。

## 0.65 · 将参考方向落实到日常足迹（2026-10-19，候选）

用户授权自行整理取舍并完成。本轮选定按天收藏/标题/备注/标签/搜索、纸白/夜色路线明信片预览保存分享、行记随既有备份恢复，并保留本分支首次默认高德简化流程。照片、复杂挑战与新空间索引暂缓；开发期间合并上游0.64，保留其单一高德、显式授权决定和历史回放。版本0.65/code73，SQLite schema3、GPS过滤、50米揭雾和原始分段保持；设计与验证见 docs/design/experience-20261019.md 和 docs/acceptance/v0.65/README.md。

合并上游后的449 JVM 与41 Python通过，开发APK/androidTest构建通过。已建立本任务专用 Google API36 软件模拟器，原生完整功能及Activity重建验收通过（1.3字体）。当前环境无正式高德配置/原签名，不能声称正式高德、实体手机或官方覆盖更新已验收；PR #104承载本轮候选，尚未正式发布。

## 0.64 · 单引擎地图、必过首启与历史回放（2026-10-19）

用户原话（本轮起点）：软件是直接通过微信分享给她用的，这样根本不适合普通用户；不要用瓦片地图，瓦片地图删掉，只用高德地图；要历史回放；道路走过次数由我决定。

分支 v0.64-single-map-onboarding，基线 bfb27a1（v0.63/code71）。已提交 a462dd4（首启地图同意改为必过整屏、地图未启用不再黑屏）与 e224ef7（历史回放），后续还有单引擎删除与文档对齐的提交。

已交付：① 首启地图授权改成不可跳过、不可点走的整屏 DesignMapConsent，未启用时地图区显示「地图还没打开」+「开启高德地图」，缩放控件改用主题令牌（深色不再白块）；② MapLibre/OpenFreeMap 备用瓦片引擎整体删除（app/build.gradle.kts 依赖、MainActivity.kt、TrackMapJson 注释、RoadColorProbe 探针、NOTICE/文档），安装包 63.5 MB → 52 MB，APK 内不再有 libmaplibre.so；③ 历史回放：Replay.kt 的 replaySlices 纯函数 + ReplayTest.kt 单测，选某天后可从起点沿当天轨迹画到终点，可播放/暂停/继续/重播/关闭，不写数据库。

验收：435 JVM（新增回放切片 4 项）、41 Python 通过；正式 ARM64 构建通过并在 emulator-5560 实测单引擎运行、回放进度单调增长到 100%、暂停冻结、100% 后可重播、关闭恢复当天卡片、五表行数保持 10/4893/0/0/24；撤回地图授权后仍显示可读可恢复的占位页，无黑屏。完整原始 JSON 在 Git 忽略的 verification/v064-replay、verification 目录。

未验收/限制：高德底图瓦片在 x86_64 AVD（含 swiftshader_indirect）仍不渲染，底图观感只能以真机为准；实体手机未连接，安装、户外、锁屏与流畅度不作通过声明。v0.63 之前的 docs/evidence 与 release notes 保留备用引擎的历史记录，不回改。

对外部评审：本轮 tools/codex-consult 两次返回 RPC account/read rejected（-32603），因此 ③ 走过次数的数据库迁移设计由我本人完成，未经外部评审。

下一步（③）：给 roads 加走过次数，数据库 v3→v4，先做迁移与回填安全设计（不丢用户数据、可回退、旧备份兼容），再按 Wandrer.earth 玩法呈现。回退须原签名、schema 兼容、versionCode 不降低。

### 0.65 原生验证进展

生产实现已通过最终41项针对性、449 JVM、41 Python与开发包/androidTest/lint（0 errors）。新增保存当前底部页的Bundle状态，系统选图期间Activity重建后仍能恢复足迹/明信片；隐私文案明确标题会进入图片。原生隔离备份恢复通过，正常及1.3字体的备注/收藏/双主题预览已实际操作并截图。系统保存返回“图片已保存”后，原工具按文件路径寻找失败，现改在androidTest中观察真实SAF返回URI，直接读取其PNG；不增加生产诊断Intent或测试出口。完整保存/分享/查找闭环已在1.3倍字体下通过，真实SAF返回URI的PNG尺寸/夜色/文件名及系统分享选择器均核对，SQLite五表摘要保持；额外Activity重建场景也已通过同一完整流程，见docs/acceptance/v0.65/picker-recreation-large.log。

本任务Google API36 AVD/emulator-5556由当前环境新建，无KVM，仅软件仿真；曾临时设硬件超时倍率10、watchdog_timeout_millis=600000、hide_error_dialogs=1、720x1600/density280、动画0，测试字体1.3；结束已恢复1080x2400/density420、动画1.0、字体1.0并删除两个全局设置，读回核对后只停本AVD。系统Settings与SystemUI曾ANR，不作系统稳定/性能声明；ADB root与只读硬件超时属性随冷启动重置。正式高德配置/原签名仍未在环境中，PR104保持候选，未发布。

### 0.65 最终主分支同步

同步 main a88a11f 的自适应底部导航与山水主题，冲突处理保留实测导航高度避让，同时保留地图记录提示的直接处理入口；最终应用源码619577d00895860b596c5b5ab7cfd2123b7bb446。重新执行41项针对性、449项完整JVM、41项Python、lintDebug（0 errors）与两个开发APK构建通过。此前原生完整流程证据继续绑定其原源码，不用新构建摘要重标旧结果；最新整合验收来源见docs/acceptance/v0.65。

最终整合原生流程已通过：应用619577d、工具d5f54d3，1.3字体下完整编辑/持久化、收藏搜索、双主题预览、真实SAF返回URI的PNG、系统分享选择器及保存期间Activity重建均通过，SQLite五表摘要保持。原始结果与截图见docs/acceptance/v0.65/merged-journey-large.log；工具修正的两次中止单独保留，不重标旧证据。

最终复测结束后再次恢复字体1.0、动画1.0、1080x2400/density420，删除hide_error_dialogs与watchdog_timeout_millis并读回核对；仅删除本任务walkpaint-card-*临时输出，随后停止本任务AVD。
