# 当前分发：v0.37 候选（DSH 分支，2026-10-12）**未发布 · 复核复检进行中**

> 本节取代下方 v0.26 段落作为当前状态。**上一轮 `candidate-v0.37n` 的 `passed=true` 已被证伪，不再作为证据**：08:52 复核指出那轮的 `fixed/map-far-cluster.png` 其实是近处街区（脚本点"返回全部探索"后 `follow.locate()` 把相机拉回了人的位置），而当时还没有相机场景断言，颜色比例断言因此误过。当前代码已再次变更（累计视图改为显示已探索范围、绘制改为逐环整体重画、掩膜 512、跳过未变化 GeoJSON、跟随视口的帧不再受 20 公里上限裁剪），**正在对新候选做受影响复验**；只有新包对应的「相机场景 + 像素 + 性能」证据作数。**高德 ARM 真机路径本机无法运行，仍未验收**；候选未发布。

- **工作目录 / 分支**：`D:\Worktrees\WalkPaint\dsh-lasterror`，`fix/dsh-lasterror-recovery`。业务改动自 `9672f2f` 起，复核后两轮见提交历史（`dd711b9`、`337b4cb` 及之后）。与 `D:\Projects\WalkPaint` 完全隔离。
- **版本**：`versionCode 39` / `versionName 0.37`；包名 `cn.walkpaint`、schema2、原 debug 签名（SHA-256 `21b74e3e…`、SHA-1 `fae3df35…`）不变，可覆盖安装。
- **候选包**：见本节末尾「候选与指纹」（复验通过后才会给出最终通用包与手机包哈希；旧 `n` 的哈希只作为"证据不足"的历史记录保留）。
- **实际测试引擎**：专用 Google AVD `emulator-5556`（`WalkPaint_Google_API36`，x86_64，SDK 36）。`AmapPlatform.supported` 只接受 ARM 进程，所以本机只能跑**备用地图（OpenFreeMap）引擎**；引擎从运行中的 App「我的 → 更多设置 → 隐私与权限」读回。**备用地图通过不等于高德通过。**
- **本轮要复验的四条**（08:52 复核）：
  1. **场景身份**：每张截图必须绑定实际相机中心/可见 bounds/累计或当天模式/人的位置；远簇截图必须真的在 `2.5163` 附近，且人是被"拖动地图"而非被移动过去的（`pan_camera` 用真实 `input swipe`）。
  2. **追加轨迹不得把已探索地面重新盖雾**：绘制改为逐"环"整体重画；像素探针新增"平行偏移 + 交叉 + 新尾巴"的逐像素断言。
  3. **整页 UI 成本**：分解计时证明 UI 块里赋值 4.6 ms、交接 55.7 ms（= 位图上传）；掩膜 768→512、跳过未变化 GeoJSON 后实测 `ui_med 18.3 ms`，**恢复原判据 20/120**；拖动改为"边持续注入定位边拖动"并断言期间确有状态更新。
  4. **宽视野不露底**：跟随视口的帧不再受 20 公里上限裁剪（细节变粗，但未知区域仍被遮住）；像素探针断言 30 公里跨度视口四角都在帧内。
- **未验收 / 开放**：① 高德 ARM 真机；② 户外长时间后台记录；③ `map_tab_ready_ms` 被测试工具 dump 延迟污染、不可比；④ 极宽缩放的观感只在模拟器上看过。
- **发布**：未上传、未建 Release、未合并 PR；沿用同一私有渠道发布需要用户明确一句确认。

## 候选与指纹

- **手机 arm64（可覆盖安装）**：`release-out/WalkPaint-v0.37-arm64-debug.apk`，70,387,176 字节，
  SHA256 `6acd23c2ee57fa9b5de005db09114de4fde96ca7fd78c6a330873353bc33fcda`；
  同目录 `WalkPaint-v0.37-SHA256.txt`。包名 `cn.walkpaint`，versionCode 39 / versionName 0.37，
  原 debug 证书（SHA-256 `21b74e3e…`）。`classes*.dex` 与下面这台模拟器实测的通用包**逐字节相同**（已核对），
  arm64 包只多 `lib/arm64-v8a/*`。**arm64 包本机装不了（AVD 是 x86_64），它没有被运行过**，
  运行证据对应通用包。
- **模拟器实测通用包（本机备用地图引擎）**：`verification/fog-line/candidate-v0.37r-universal.apk`，
  SHA256 `be5ffcc600c903d28930bc99b976daba4ff6208c79f89153ef40860c81fc0695`。
- 这一轮已确认的证据（r 包）：像素探针 `FOG_PIXEL_PROBE_PASS`（含"追加偏移/交叉/新尾巴不得重新盖雾""宽视野四角都被覆盖"）；
  性能计数健康：`ui_med 14.4 ms`（原判据 ≤20）、`upload_med 1.6 ms`、`veil_inc_med 3.5 ms`、`pan_med 1.7 ms`；
  拖动期间确有状态更新（`gesture_counter_delta.update_n = 4`）。
- **历史（证据不足，仅存档）**：`candidate-v0.37n-universal.apk` SHA256 `951c72bf…`（那轮 `passed=true` 因缺少相机场景断言而不成立）；`candidate-v0.37q-universal.apk` SHA256 `33315a46…`（相机场景断言正确判它失败：宽视野下平移步长过粗，相机被推到 54.2°N）。
- 被替换的基线：`verification/fog-line/baseline-v0.36-universal.apk`，SHA256 `98d020197ccfb50daaad63db5794be86eac51d2afb9a8168c9bc14a3014e3a6`。
- **同一场景复现**（`verification/fog-line/before/map-follow.png`）：同一条街走 4 趟的匿名夹具上，基线画出**四条并排深绿线**（麻花）+ 线端圆点（串珠），整屏被 550 米网格染色铺满（方块）。修复后同一视角（`after/map-follow.png`，单簇夹具）是连续柔和的雾 + 约 20 米柔边涂色带。
- **实测数值**（**上一轮 n，证据不足，仅作历史**；新候选的数值在复验通过后替换本节）：那一轮 `passed=true`、主机可用内存 1340 MB；雾交接中位 1.7 ms、迷雾增量中位 6.1 ms、UI 总量中位 37 ms、拖动期间状态更新 0 次、远簇 `veil 0.577 / tint 0.107`。**其中"远簇"与"拖动 0 次更新"两项已被证伪**（前者场景不对，后者是把压力抽掉）。
- **本轮已在本机测到的（同一台机器、主机很忙、`prep_med` 1610 ms）**：UI 总量中位 **18.3 ms**（原判据 ≤20）、雾交接中位 **6.9 ms**、迷雾增量中位 8.4 ms；拖动改为边注入定位边拖动后，`gesture_counter_delta.update_n` 为 **4**（证明压力真的发生了）。
- **像素自证**：`FOG_PIXEL_PROBE_PASS`（走廊只带染色、雾从涂层外缘渐变回升、帧边界真渐隐、**重复经过逐像素不变**、**追加偏移/交叉/新尾巴不得把已探索地面重新盖雾**、12 公里视口帧包含该地面、30 公里跨度视口四角都在帧内）。这套断言抓到的缺陷包括：`CLEAR` 是二进制清除、边框渐隐用了 STROKE 画笔、`DST_OUT` 会让重复经过越擦越薄、**追加线段的外环重新盖雾**、**复用缓冲区时 `SRC_OVER` 让雾越叠越深**。
- **大范围历史（复核第 1 条）**：待新候选复验——上一轮那次"远簇已修"的结论**不成立**（截图其实是近处）；本轮加了相机场景断言，并且发现产品路径本身也到不了远处（累计视图在 zoom≈21 时一次滑动只挪 30 米），因此把"返回全部探索"改为**显示已探索范围**（`showExploredExtent`，超过 800 米时按边界取景），用户才能看到远处旧足迹。是否真的看到，以新候选的 `scene_far_cluster` 断言与截图为准。
- **未验收 / 开放**：
  1. **高德 ARM 真机**：迷雾观感、拖动帧率、涂色深浅需用户真机确认。
  2. **视野上限**：雾帧跟随视口，上限 20 公里；视野比 20 公里更宽时帧被截到 20 公里，四角没有雾（夹具未覆盖这一档）。
  3. 户外长时间后台记录。
  4. `map_tab_ready_ms` 被测试工具的 dump 延迟污染，不可比。
  5. 本机这版 MapLibre（13.5.2）上 `ImageSource.setImage/setCoordinates` 不刷新画面，故每次改动重建 source+layer（实测 UI 线程 2–7 ms）；这是本机行为，未逐版核对上游。
- **发布**：候选与证据就绪，**未上传、未建 Release、未合并 PR**；沿用同一私有渠道发布需要用户明确一句确认。
- **证据**：`docs/evidence/V037_FOG_LINE.md`、`verification/fog-line/{before,after,fixed}/`（acceptance.json + 截图 + history）、脚本 `scripts/android_fog_line_acceptance.py`（fail-closed）、`scripts/android_reopen_probe.py`、`scripts/run_fog_acceptance_fresh.py`。

## 工具练习：Perfetto 离线入口（合成 trace，**不是业务成绩**）

按 `android-performance` 技能第 1 题，用已安装工具独立分析 `D:\AI\Tools\android-performance\exercise.json`（目标进程 `org.example.coaching`），输出到本工作树 `verification/perf-drill/drill1-exercise-summary.json`（唯一文件名，未覆盖任何旧报告）。命令与返回码：

```powershell
python -X utf8 D:\AI\Tools\android-performance\analyze_trace.py --trace 'D:\AI\Tools\android-performance\exercise.json' --package 'org.example.coaching' --out '...\verification\perf-drill\drill1-exercise-summary.json'
# status=observed, frames=not_measured, exit 0
```

结论与边界（依据观察值，不迁移数字为产品阈值）：

- **值得优先查的是 `render_prepare`**：主线程 2 次、合计 220 ms、单次最大 122 ms；而 `bitmap_upload` 只有 1.7 ms。这正是"上传很快、整页仍可能慢"的形态——先看主线程那段，而不是盯着便宜的局部步骤。
- **不能证明拖动不卡**：`frame_metrics.status=not_measured`（帧数据为 0），trace 里也没有手势与更新的重叠信息；且片段可嵌套，不能相加当总耗时。
- **不能证明手机已验或最终包正确**：这是 442 字节的合成 trace、只有一个进程，既没有设备/引擎/APK 身份，也没有真实操作时间线。
- 工具语义：`exit 0` 只代表"目标进程有观察值"，报告自身写着 `product_acceptance: not_verified`；因此本段只算入口调用与结论边界的练习，**不计入任何业务验收**。

## 环境层结论（有复用价值，不是产品成绩）

反复出现的三类症状——**安装后 `dumpsys package` 返回成功但读不到版本**、**截图里 `veil_ratio` 只有 0.01**、**颜色/性能数值忽好忽坏**——经最小定位分成两层，不要混为一谈：

1. **harness 查询层**：`run()` 在非零退出/超时时抛异常，而实际遇到的是"命令成功、输出为空"（安装刚结束时 PackageManager 未就绪）。已改为轮询并记录 `version_query_raw` / `version_query_attempts`，三层（空输出 / 错误串 / 解析失败）可区分。
2. **模拟器窗口层**：截图里雾"不可见"的那次，屏幕上是 **"System UI isn't responding"** 弹窗；`dumpsys window` 显示 `mFocusedApp=cn.walkpaint/.MainActivity` 但 **`mCurrentFocus=null`**，而设备是 Awake、屏幕 ON。用只读工具 `inspect_emulator.py` 判定为 `blocked / foreground_unobserved / stage=foreground_1`；执行一次 `am force-stop com.android.systemui`（不重启 AVD、不动数据）后复测得到 **`observed_ready / two_matching_observations`**（`mCurrentFocus` 恢复为 `Window{… cn.walkpaint/cn.walkpaint.MainActivity}`）。观测文件：`verification/ready-r-round2.json`、`ready-r-round2-app-foreground.json`、`ready-r-round3-after-systemui-restart.json`。

结论边界（工具自己写的）：`observed_ready` 只说明这两个瞬间**版本/前台可观测**，**不证明渲染正常、没有遮挡、性能合格，也不证明 APK 哈希相同**；版本码相同不等于包相同，所以安装指纹（`installed_apk_sha256` 与候选哈希逐字节比对）与产品场景断言仍然保留。`blocked` 时不得把截图颜色或空样本判为产品回归。

## 逐项状态（不许汇总成"都解决了"）

| 项 | 状态 | 依据 / 会拦住什么坏结果 |
|---|---|---|
| 迷雾不是方块，是连续柔边带 | **通过**（r 包，模拟器备用引擎） | `fixed/map-follow.png` + 像素探针走廊/渐隐断言；旧包同场景 veil=0.002（新判据会失败） |
| 默认地图不画轨迹线、没有串珠 | **通过** | 累计视图 `mapTrailPaths` 为空（产品逻辑）+ 截图无圆点 |
| 点选某天只画一条线（无并排双线） | **通过** | 新判据"扫描线上两条线起点间距 ≥20px"：候选 282/476 px；**旧包 4 px，判据会失败** |
| 拖动/缩放/进入地图页不卡 | **部分通过** | UI 线程中位 12.8–18.3 ms（原判据 ≤20）、交接中位 1.0–1.6 ms、拖动期间确有状态更新（`update_n`=4）；冷启动在主机内存紧张时不稳定，未达"稳定判定" |
| 平移到远处旧足迹仍涂色、未知仍遮住 | **渲染已证**；端到端"真实拖动到远簇"待健康环境复跑 | 日志证明帧随相机到远簇（`frameLon=2.5126..2.5199`）且位图中心指纹是颜料 `540c8c76`；相机场景断言（`scene_far_cluster`）已就位。补充探针用 debug `moveCamera` 移相机**不暂停跟随**，相机随后被拉回，所以它拍不到远簇画面——**debug 钩子不能替代真实拖动**，真实手势才会暂停跟随 |
| 切换页/重开后探索仍可见、未知仍遮住 | 判据**已证明对旧包敏感**；候选端到端待健康环境 | 离线校验：旧包 `map-tab-return.png` veil=0.001 → 新判据（≥0.25）会失败；旧包并排线 gap=4px → 新判据会失败 |
| 追加轨迹不把已探索地面重新盖雾 | **通过**（像素探针；旧逐线段逻辑会失败） | `FOG_PIXEL_PROBE_PASS` 第 5 条 |
| 宽视野四角不被揭开 | **通过**（像素探针第 7 条 + 跟随视口的帧不再按 20km 裁剪） | `FOG_PIXEL_PROBE_PASS` |
| 安装不丢数据 | **通过** | 安装前后逐行一致；`--skip-install` 轮次该项标为不适用，但仍核对已安装包哈希与候选一致 |
| 高德 ARM 真机 | **未测** | 本机 AVD 是 x86_64，`AmapPlatform.supported` 拒绝 |
| 户外长时间后台记录 | **未测** | 需真机 |
| 极宽缩放的观感 | **未测** | 只在模拟器上看过 |
| 环境层（安装查询 / SystemUI 窗口） | 已定位并给出可复用判据 | `verification/ready-r-round*.json`；`blocked` 期间不得把颜色/性能判为产品回归 |
| 本轮最后一次整轮运行 | **被环境阻塞，非产品结论** | 同一包 r 在健康时 `pan_max 104.6 ms`、冷启动中位 6.0 s；环境劣化时（主机可用内存 372 MB）同一包 `pan_max 965.2 ms`、冷启动中位 18.7 s，全项约 3–9 倍变慢；`fog_pixel_probe` 仍 PASS、拖动期间 `update_n=4` |

---

# 当前分发：v0.26（DSH 分支，2026-10-10）

> 本节由 DSH 追加，**取代**下方 v0.16 段落作为当前状态；v0.16 及更早内容保留为历史，不要据此判断现状。

- **分支**：`fix/dsh-lasterror-recovery`，基线 v0.16（`b84f132`）。标签 `v0.17` … `v0.26` 逐版推送。工作副本在 `D:\Worktrees\WalkPaint\dsh-lasterror`，与 `D:\Projects\WalkPaint` 完全隔离，**主副本未被改动**。
- **最新发布**：`v0.26`，`WalkPaint-v0.26-arm64-debug.apk` 70,354,212 字节，SHA256 `f93615b21ea69c9d4ec126baba67e46da8b8edc2fea971e7160721948be950b9`；服务器 digest、重新下载、本地构建三方一致。
- **升级兼容**：`versionCode` 19…28 递增，`versionName` 0.17…0.26；签名 SHA-1 `fae3df35…` 与 v0.1 起所有版本相同，schema2 未动，可覆盖升级、不丢足迹。
- **每版验收口径**：JVM 单测（v0.26 为 251 个全过）、Google API36 插桩回归 `V05_ISOLATED_DATABASE_PASS`、模拟器实拍、发布包指纹三方核对。逐版明细见 `CHANGELOG.md` 新增段。
- **关键实现位置**：`Explorer`（点亮格子、长条合并、连续天数）、`Diagnosis`（结论 + 症状编号）、`MovementCue`（是否真在移动）、`YearHeatmap`（年 / 月 / 日与 24 小时条）、`UiTokens.kt` + `Theme.kt`（中性纸白与单一颜料色）、`AmapMapController`（手势期间不重建、点亮层、街道级定位）。
- **等用户真机回答两件事**：① 高德底图上"点亮过的地面"淡色深浅是否合适；② 户外长时间锁屏记录是否连续。这两条决定下一步是调视觉，还是回去查记录链路。
- **已放弃的做法，不要重走**：探索度百分比（分母无界，实测恒为 0%）、把道路包作为前置条件（v0.16 转向的原因）、毛玻璃 / 模糊层（可读性差且吃 GPU）。
- **本地工具**：已装 Maestro CLI 于 `D:\Apps\Maestro\maestro\bin\maestro.bat`（2.11.0，含 `hierarchy` / `mcp`）。检索用工作区 `websearch.py`，不要用 DSH 内置 `web_search`（该 provider 早已禁用）。

---

# 当前分发：v0.16 私有预览已发布（2026-10-10）

用户在本chat回答“好”，明确批准上传v0.16私人预览；按既有私有渠道先草稿上传3资产、服务器大小/digest验收，再发布prerelease并实际重新下载手机包验SHA通过。仓库private=true、draft=false、prerelease=true，标签固定b84f13200e7ca6f28fbefc062e70e515a22fc55c，不移动标签；生产代码与已验收7c3ce8a相同。旧v0.15保留。[发布](https://github.com/h516df/walkpaint/releases/tag/v0.16) / [手机APK](https://github.com/h516df/walkpaint/releases/download/v0.16/WalkPaint-v0.16-arm64-debug.apk)。

高德联网地图直接涂色，不需要准备道路包。code18/schema2/cn.walkpaint/原证书；手机70,271,924字节，SHA256 18e04e055c58f0cce5199f66f8149f2c5485a9e28f5ac883d16e4ac1ab372484。186 JVM/7 guard与Google直接轨迹/日期/颜色/联网备用地图/重开/320dp/旧五表保留，详见V016_DIRECT_TRAILS.md；分发校验见v016/publication.json。实际下载成功，不代表手机已安装，新ARM高德/真实驾车/长期后台仍未验收，#4/#6保留。PR #23仍草稿未合并，无Actions。专用AVD此前已关闭。

可在“我的→更多设置→检查更新”取得该版本：发布说明文件名/版本码和arm64资产名满足现有客户端选择规则，API实读显示为最新可见发布。首次授权与手机系统安装仍需本人确认，没有读取手机或假称已更新。只允许同签名schema2/code>=18兼容修复包回退，禁止卸载清库。

---

# 当前 v0.16：联网高德直接轨迹涂色候选

按用户最新明确要求，取消准备地图前提，在应用内高德联网地图上直接显示已保存GPS轨迹。旧版道路包/道路匹配/禁止GPS折线契约已被本次需求替代。单写手agent/codex/direct-trails，基线f202d8a，目录本chat work/worktrees/codex-diagnostic-acceptance/codex-direct-trails。

186 JVM/7 guard、Android无道路隔离库步行/骑行/驾车/断档、Google实际直接轨迹/日期/换色/联网备用底图/重开/320dp及原五表保留通过；code18/schema2/原签名手机候选和源指纹见docs/evidence/V016_DIRECT_TRAILS.md、v016/acceptance.json。本机有签名验证与同dex证据；ARM高德及vivo户外长期后台未验收，#4/#6保留。不得操作MuMu/手机、卸载/清库或把公开模拟夹具当真人行车。测试原偏好/视口已恢复，专用Google App停止，专用Google AVD已通过身份守卫关闭，adb实读无设备。

生产提交7c3ce8a975bb646a01354f8de4b942a6b1367680；草稿PR #23 https://github.com/h516df/walkpaint/pull/23 已推送/可合并但不合并，无Actions。现有项目索引PR agent-rules#29仍Active且开放，无需重复登记。

v0.16尚未发布，需本次明确确认。v0.15已发布，不重复询问v0.15授权。不合并依赖历史PR。当前已保存GPS直接画，旧道路表仅兼容保留；旧未入库定位不能恢复。同签名schema2/code>=18修复包回退。

---

# 当前分发 — v0.15 私有预览已开放（2026-10-10）

用户在本chat明确选择“确认发布 v0.15 私有预览”。在现有私人仓库先创建草稿并上传3资产，逐个服务器大小/digest核对通过后开放prerelease，实际重新下载手机APK验SHA通过；private=true/draft=false/prerelease=true。标签v0.15固定业务及完整交接提交d5f33de2cb109ee697f443d010cd7936e5f78c90，生产源码与已验收业务b9e8a2b相同；不移动标签。原v0.14资产保留。[发布](https://github.com/h516df/walkpaint/releases/tag/v0.15) / [手机arm64APK](https://github.com/h516df/walkpaint/releases/download/v0.15/WalkPaint-v0.15-arm64-debug.apk)。

code17/0.15/cn.walkpaint/schema2/原证书，手机70288416字节，SHA256 2b18acd1917b7d199cb9405fb97d0df5b321687c3c1c2e571b9166afeecfec89。服务器3资产和实际下载匹配见docs/evidence/v015/publication.json；180JVM/7guard/Google真实UI及数据保留证据见V015_DIAGNOSTICS.md。PR#22保持草稿、未合并，无CI。

v0.14应用内更新可检查该新版，首次仍需本人GitHub只读授权与手机系统安装确认。**没有访问用户手机，不能说已更新或行车问题已修好。** #6驾驶/长期后台、#10可信速度桶与完整保存→匹配→渲染窗口仍未完成。不上传私人足迹或凭据；只能同签名/schema2/code>=17兼容包回退，不卸载/清库。

---

# 当前本地候选 — v0.15 过滤诊断完成实际 UI 验收（2026-10-10）

本节优先。独立分支agent/codex/diagnostic-acceptance，目录D:\Workspaces\Codex\2026-10-10\ji-x\work\worktrees\codex-diagnostic-v014\codex-diagnostic-acceptance；诊断基线ea691b0，业务b9e8a2b4fd28338bbc4101c6ff21719769098baa。

**状态：180 JVM/7 guard/专用Google真实诊断、反馈复制、小屏及数据保留通过；v0.15/code17/原签名/schema2手机候选就绪，未发布/安装手机，不合并。**详见docs/evidence/V015_DIAGNOSTICS.md。五分类和窗口沿用上一诊断候选，不改变GPS、matcher或DB。两包dex/说明一致。5表trips3/points19/roads6/road_matches16/coverage1逐行保持；合成诊断偏好/视口恢复，AVD停止。

真实vivo驾车/长期后台未验收，#6开放；可信速度桶及保存→匹配→绘制各阶段窗口仍在已有#10，不把此候选说成全部完成。v0.14私人下载继续为已发布保底；本地旧诊断曾沿用code16而未发布，现候选明确增为code17。只能同签名/schema2/code>=17修复包回退，不卸载清库。私人发布需本轮具体确认；源码草稿PR属于内部交付。

---

# 当前本地候选 — v0.14 GPS 过滤诊断增强（2026-10-10）

本节优先于以下历史。唯一写手 `D:\Worktrees\WalkPaint\codex-diagnostic-v014` / `fix/v014-diagnostic-breakdown`，从此 worktree 当前 v0.14/code16 的 bcec979015593e67f8ae3567bcbce4bfec0f5f9b 实现，不使用旧 main、不修改其他目录/分支。用户真机反馈的1088有效GPS/63入库/1025过滤仅为输入线索；未收到对应行车分类数据，不能认定原因是停车或跳变。

**状态：代码及本地通用 APK 已就绪，180 JVM/7设备守卫测试/构建通过；合规 Google AVD 未启动，真实 UI 和 Android16 真机行车尚未验收。未安装、上传、发布、推送或合并。** 详细测试、APK指纹与复现命令见 [V014_DIAGNOSTICS](docs/evidence/V014_DIAGNOSTICS.md)。

诊断摘要和主动预览反馈包新增本次服务过滤五类计数（静止/小位移、JUMP_OR_TOO_FAST、精度、时间、其他），与四阶段计数独立。旧版计数不倒推为其他，明确列出不可回溯数量。窗口复用原 ProblemHistory 最多500条、7天、单原因最多5分钟的聚合；显示30分钟/2小时完全落在窗口内的桶，以及跨起点桶的全部次数，禁止分摊；关闭/清空/裁剪/升级前未记录的缺失明确提示。报告窗口统计使用全部保留桶，不受线索列表30行展示上限影响。无行车识别或完整窗口覆盖的推断。

五个固定 Long 计数沿用原诊断线程和30秒批量保存，重启清零、重载保留；未知过滤文本在入缓存前归一到 OTHER_FILTER，历史仍用固定枚举。无坐标/原始速度轨迹/道路名或敏感ID；无新上传、服务或数据库迁移；GPS/道路逻辑、阈值和旧数据未改。可选已保存道路最近窗口复算本轮未增加，现有最近最多2000段的当前目录只读复算保留。

回退：回退本次源码差异即可；schema2/原签名/code16保持，新增偏好计数被旧版忽略。禁止用卸载/清空数据来回退；若未来正式分发，应另行核对签名、版本及目标环境覆盖升级。此候选不能代表用户手机已生效，也不能关闭原行车涂色问题。

---

# 当前交付 — v0.14 私有更新 / #11（2026-10-10）

唯一写手 `agent/codex/issue11-private-updater` / `D:\Workspaces\Codex\2026-10-10\new-chat\work\worktrees\WalkPaint\codex-issue11-private-updater`，业务与 code16 APK `bcec979015593e67f8ae3567bcbce4bfec0f5f9b`，继承 ed6ee80；[草稿 PR #21](https://github.com/h516df/walkpaint/pull/21) 未合并。**应用内更新软件闭环通过；v0.14 私有预览已开放，手机未安装/未验收。** 171 JVM/7 guard，真实 App 授权、私有 manifest/下载、取消重试、拒绝来源、取消系统安装、确认覆盖 code15→16、安装 SHA、原五表逐行保留、重开核对最新0.14、退出授权及 Keystore/key/cache 清理通过。仅 Google AVD，已停止。详情 [V014](docs/evidence/V014.md)。

用户同意仅 walkpaint Contents/Metadata read App；实际安装范围仅本仓库。自动审核初拒绝发布，用户之后明确“确认”开放 v0.14，按原命令发布成功；private=true/draft=false/prerelease=true，标签业务 bcec。4 个资产 digest/size 和实际手机包重新下载匹配。不使用 server/secret/private key/PC token，非秘密公开 client id；临时 Google 本机授权已清除，原用户浏览器登录和 App 只读安装保留。

手机 arm64 70288352 字节，SHA `04985a845d7cd882995097bd106823e6ec052ccde2ee79b4a76ab0d8b0bfddf2`；实际 Google 通用包 SHA `f2ba1250fe50690b599f1cb286a62427cf923eb0432399e8b3ee3f5e09e8e0f4`。包名 cn.walkpaint / 原签名 / schema2 / code16，两包 dex/说明一致。code15 为本地引导测试包未分发，故验收旧/新界面均显示0.14但码15→16；正常手机0.13→0.14。完整测试在安装后系统 Done 窗口导航处失败；原失败 JSON 保留，补真实 Done 处理后在相同已安装最终 APK 续验剩余链路通过，不虚称全轮重跑。

vivo 户外/长期后台/ARM 高德仍未验收；#6/#7/#8/#10/#11/#12/#17 不擅自关闭，没有 CI，不合并。不动 canonical/其他 worktree/私人 MuMu/手机，不改记录、数据库或 matcher。回退仅同签名/schema2/code>=16 修复包覆盖，不卸载/清数据。[私有预览](https://github.com/h516df/walkpaint/releases/tag/v0.14) / [手机 APK](https://github.com/h516df/walkpaint/releases/download/v0.14/WalkPaint-v0.14-arm64-debug.apk)。

---

# 历史交付 — v0.13 / #10 #11 #12 #17（2026-10-10）

本节优先于下方历史。用户要求“你先都做完，再上传吧”：先完成软件整合/验收，再统一私有分发，不单发v0.12。唯一写手串行工作区 `D:\Workspaces\Codex\2026-10-10\new-chat\work\worktrees\WalkPaint\codex-issue11-updates` / `agent/codex/issue11-updates`，最终业务SHA `349cd9d528a5693aa49ebda2a040da487fa89a3c`，基线PR #16 c7bba2c；地图/反馈分支各只有自有新提交。原canonical/其他写手目录未修改。

**状态：本轮软件范围完成、129 JVM/7 guard/Google实际UI与新GPS彩路通过，安装包本地就绪，v0.13私有预览已开放，服务器与实际下载指纹核对通过；PR未合并，用户手机未更新/未验收。** #11是明确浏览器降级MVP，不是自动检查下载。#6原vivoP0与#7/#8/#12真机仍开放。详见 [V013](docs/evidence/V013.md) 和内置中文 release-notes.json。

cn.walkpaint / code14 / 0.13 / 原签名 / schema2 / arm64，70238534字节，SHA256 `621efc671b464eecdde35b5d2604e13b7e43e5bd5616db0cf9e946c6036b58f4`。通用实际安装 `0264bfd34fc8d27a77d56458c5ce59ad06b5a2954b55fe6d18b9efe9fbf4135e`；两包dex与说明一致，旧行保留。反馈脱敏/预览/复制/系统分享选择器/独立清空/关闭重开通过，未自动发送。私有发布页真实浏览器目标与未知最新版返回通过。正常25分钟新行程边界后，前后台/短时锁屏实际GPS写入，公开道路50.04米/7508彩路像素，六色相机跟随探针通过。Google高德缺ARM库根因已定位，已守卫回退MapLibre；ARM高德未在本轮验证。

仍不操作MuMu/手机，不改录制、DB、matcher门槛，不嵌认证、不建服务、不公开。服务器大小/digest及发布后实际下载SHA已核对；详见docs/evidence/v013/publication.json。旧v0.11下载保留，v0.12不另发布。登记agent-rules PR #29仍Active，无生命周期变化不写版本流水账。CI未配置。回退同签名/schema2/code>=14覆盖包，禁止卸载/清数据。

迟到系统退出信息误删新线索已由349cd9d修正，129项通过。最终APK反馈/GPS/像素/SDK复验完成；浏览器/隔离库/320dp是f6f4514首轮候选上的未改UI证据，详细阶段与源SHA在V013/JSON中，不伪称全部整轮重跑。

私有预览：[v0.13](https://github.com/h516df/walkpaint/releases/tag/v0.13) / [手机APK](https://github.com/h516df/walkpaint/releases/download/v0.13/WalkPaint-v0.13-arm64-debug.apk)。目标业务`349cd9d528a5693aa49ebda2a040da487fa89a3c`，70238534字节，服务器/本地/实际重新下载SHA256 `621efc671b464eecdde35b5d2604e13b7e43e5bd5616db0cf9e946c6036b58f4`一致，原证书/schema2/code14。draft=false/prerelease=true/private=true。PR #18地图兼容/#19反馈/#20更新整合均草稿未合并，已在本chat附加；v0.11保留，v0.12不另发布。手机仍未安装，vivo未验收，#6/#7/#8/#10/#11/#12/#17不擅自关闭。

---

# 历史交付 — v0.12 / Issue #12（2026-10-10）

[草稿 PR #16](https://github.com/h516df/walkpaint/pull/16)，已推送、未合并。

**状态：候选 UI/授权/实际模拟记录已验收，手机 APK 本地就绪；待具体私有分发确认，未发布/合并，vivo 未验收。** 本节优先于下方历史。唯一主写手：`D:\Workspaces\Codex\2026-10-10\new-chat\work\worktrees\WalkPaint\codex-issue12-simple-profile` / `agent/codex/issue12-simple-profile`，业务源码 `92a22262cd2283b35c5b54330b3e2d8b68d164ff`，基线 PR #15 `3f9604d`；不从过期 main 重做，不修改其他写手目录。

“我的”简化为自动记录/涂色颜色/更多设置；备份、隐私、地图数据和诊断保留在二级。沿用原记录服务和数据/阈值；后台授权独立、通知按序、设置返回继续，已有权限不重复弹。#10/#11 未开发。111 JVM + 7 Google guard + Google API36 实际 UI/隔离 DB/1→4 点前后台及短时关屏/关闭停止/首次取消/320dp/颜色入口/地图下载披露取消通过。测试有分段修正与针对性续验，详情 [V012](docs/evidence/V012.md)；不把历史 MuMu 证据算本轮 Google 通过。

手机 `cn.walkpaint` / code13 / 0.12 / 原签名 / schema2 / arm64；70,188,579字节，SHA256 `de5963bf3dcbdce1af4d051cc116d5aa20b2a0e283f2f865b2351239a7bae3d2`。本会话 outputs `WalkPaint-v0.12-arm64-debug.apk`。通用/Google 已装 `1fb41afc85bc389efc68cfc1188b2f7aff868e0bc01fe4261b463c1bbff310e6`，二包 dex 一致。手机未安装，APK 未上传。原 v0.11 私有下载保留。

Google AVD 高德地图黑屏在旧 v0.11 基线已复现，底图/彩色道路像素回归未通过；单列 [Issue #17](https://github.com/h516df/walkpaint/issues/17)。vivo #6 GPS 原故障、#7 朝向、#8 颜色/历史与本次权限交互/长期后台仍待真机；保持各 Issue 开放。#12 本阶段 UI 不等于 P0 修复。CI 未配置。回退只用同签名/schema2/code>=13覆盖包，禁止卸载/清数据。

---

# 当前交付 — v0.11 / Issue #8（2026-10-09）

本节优先于下方历史。唯一主写手串行从 v0.10 / PR #13 `0a5c6ada079b327f9948115146dfc8ad3477871d` 开发；工作区 `D:\Worktrees\WalkPaint\codex-issue8-road-colors`，分支 `agent/codex/issue8-road-colors`。原 canonical/#6/#7 工作区保持干净、未修改；本次 PR base `issue-7-map-follow`，依赖未合并 PR #13，不合并。[草稿 PR #15](https://github.com/h516df/walkpaint/pull/15)，已推送、未合并。

**状态：v0.11 本地生产 APK、103 项 JVM 与 MuMu 高德/MapLibre 实际入口通过；用户本轮确认后私有 APK 已分发、服务器与重新下载校验通过。真机视觉/方向/长期后台与 #6 GPS P0 未验收。** 我的→涂色外观，五预设/六位自定义/恢复青绿，保存到本机并即时重绘全部已确认道路；不改未走灰线/蓝点/描边，保持相机与跟随；不改录制、DB/schema2、matcher/GPS门槛和备份格式。偏好不包含在轨迹备份。

业务/验收源码 `d8cb3ba4ca8fc401756e040c56bd039b310ae9e5`；cn.walkpaint / code12 / 0.11 / 原签名 / arm64。`WalkPaint-v0.11-arm64-debug.apk`：70428652字节，SHA256 `ec5066db7c0424327597d1ca67105ced83cccaf82648e03f74492757fe0f5994`，实际安装相同。五张表换色前后完全相同、230→230点；跟随及重复走路回归添加公开模拟测试点到240，原行保留。正反向重复覆盖110.00→110.00米，蓝点位移0px，历史与320dp通过。两SDK各验证6色及相机/跟随；清理无错误。

证据/公开Oxford截图/测试失败说明：[docs/evidence/V011.md](docs/evidence/V011.md)。早期颜色全轮SDK测试拿到销毁Activity，原失败JSON保留；修正测试后在同一生产APK上余项与跟随/重复走路通过，没有伪称全轮重跑。无GitHub Actions，不称CI通过。

**v0.11 私有预览下载已开放，手机是否安装仍未核实。** 用户本轮明确选择“上传 v0.11 私有预览版”，完成草稿上传→服务器大小/digest核对→私有prerelease→实际下载验SHA；[发布](https://github.com/h516df/walkpaint/releases/tag/v0.11) / [手机APK](https://github.com/h516df/walkpaint/releases/download/v0.11/WalkPaint-v0.11-arm64-debug.apk)。private=true/draft=false/prerelease=true，标签固定已验收业务`d8cb3ba4ca8fc401756e040c56bd039b310ae9e5`，70428652字节及上述SHA一致。实读记录见docs/evidence/v011-publication.json。仅上传APK，仓库仍私有；图文证据仅公开测试区域，没有私人足迹。原v0.10下载保留，不把分发当手机已更新或真机GPS/方向/颜色验收完成。覆盖安装，勿卸载/清库。 回退同签名、schema2、code>=12修复包，不卸载/清库。

项目登记已有独立agent-rules草稿PR #29（Active），未合并；无新增项目状态变化。#6/#7/#8保持开放，本阶段范围已经达到软件验收边界，真机结果到来前不扩大任务。

---

# 当前交付 — v0.10 / Issue #7（2026-10-09）

草稿 PR：[PR #13](https://github.com/h516df/walkpaint/pull/13)，已推送，未合并。仓库当前未配置GitHub Actions工作流，PR checks为空；100项本机JVM/构建/实际UI与SDK验证已完成，不能把空checks写成CI通过。

本节优先于下方历史。用户继续开发后，以 #6/v0.9 模拟器稳定基线5c3bcee串行实现 #7；唯一主写手，`D:\Worktrees\walkpaint-issue7-follow` / `issue-7-map-follow`。原 canonical checkout 与 #6 工作区干净、未修改。#6真机仍开放，#8还未开始。本次PR base为 `issue-6-gps-road-diagnostics`，依赖PR #9，不合并。

**状态：v0.10候选生产APK、100项JVM、MuMu高德/离线实际UI、方向换算和SDK相机/监听生命周期全部通过；vivo手机旋转、长期后台与原GPS P0尚未验收。** 自动位置＋方向跟随，手动浏览暂停，原定位按钮恢复，历史保持；只改显示交互，不改RecordingService/DB/matcher/GPS保存门槛。原签名/schema2/code11，业务/测试 `3728c18b7e5ce834048e717d2ad63e6c31cdf909`。

本地 `WalkPaint-v0.10-arm64-debug.apk`：70375792字节，SHA256 `cc510ff035a7b039828764d46b58d45179642715d92b101a2def4315956807d9`，安装指纹相同。原版偏移299px→新版0px；原219点全行保留到224，离线分项227→230；SDK专项所有五张数据库表不变。测试清理无错误。详细截图、失败记录、官方依据与未测项见 [docs/evidence/V010.md](docs/evidence/V010.md)。

**v0.10私有预览下载已开放，手机是否安装仍未核实**。用户在本轮明确选择“上传私有预览版”，完成草稿上传→服务器digest/大小核对→私有prerelease→实际下载验SHA；[发布](https://github.com/h516df/walkpaint/releases/tag/v0.10) / [手机APK](https://github.com/h516df/walkpaint/releases/download/v0.10/WalkPaint-v0.10-arm64-debug.apk)。private=true/draft=false/prerelease=true，标签v0.10固定业务3728c18，70375792字节及上述SHA一致。原v0.8下载保留，原v0.9仍未另建Release；不得把预览分发当真机P0/方向验收完成。实读见docs/evidence/v010-publication.json。手机只需覆盖安装、系统确认和实际使用，不需要USB/命令。回退同签名、schema2、code>=11修复包，不卸载/清库。#7不因模拟器通过关闭；下一代码任务是独立#8颜色自定义，仍单写手串行。

---

# 当前交付 — v0.9 / Issue #6（2026-10-09）

本节优先于下方v0.8历史记录。唯一主写手，从核对一致的f987dc7/v0.8建独立分支 `issue-6-gps-road-diagnostics`，工作区 `D:\Worktrees\walkpaint-issue6-p0`。原 `D:\Projects\WalkPaint` / PR #1/#3/#5/main 未修改或合并。对应任务 https://github.com/h516df/walkpaint/issues/6。

草稿 PR：https://github.com/h516df/walkpaint/pull/9，base `feat/v08-map-first`，不合并。**v0.9 APK 尚未上传、Release 未创建**（2026-10-09远端实读为空）；自动审批审核拒绝创建私有草稿Release及上传APK，理由是需要本次具体分发的明确确认。用户确认前不要换渠道/工具上传；现有v0.8下载保留。本地APK、完整验收、发布说明与指纹均已准备，确认后可上传草稿→核对服务器digest/大小→开放私有测试→实际下载验SHA。未上传不能给有效v0.9手机链接。

**状态：软件修复 + MuMu实际APK全链 PASS；vivo原无GPS回调/历史道路0米根因仍未知，真机 BLOCKED；#6开放，#7/#8暂缓。** 修复健康检查异常后停止续排；新增本会话四阶段计数、GNSS无坐标状态、道路拒绝细分。没有换GPS来源、放宽精度/歧义/断档门槛、改SQLite/schema2、自动下载或上传定位。

业务 `ba4802a` / 验收源码 `ba4802ade8c9c1251da4e41b40bc268f4b48b2ba`；code10/name0.9/原签名/arm64，APK 70366783 字节，SHA256 `580e5eefb37f6eff81a9a9a127c95fb9bb1cf12054724e007abcb453d5a9823c`，实际安装相同。94 JVM零失败；Android真实主Handler异常恢复与隔离库、MuMu从未走灰段→连续约30米着色→反向去重→手动重开保持、后台与短时锁屏、权限/暂停/撤回/诊断/小屏通过；原198点全行保留到217点，道路/历史匹配与旧覆盖区间保留。截图/失败/清理/官方依据及边界见 docs/evidence/V09.md 和 verification/issue6-delivery-final/acceptance.json。

手机下一步仅覆盖安装、室外正常走路含锁屏，再提供“我的→记录与道路诊断”的无坐标文本。用户远程，不要求USB/命令/开发工具。只有这份真实新回调/入库/连续上色证据能推进#6，不因模拟器通过关闭。若GPS仍零回调，GNSS状态仅是诊断线索，未观测不等于无卫星；不把network蓝点当可靠GPS。回退构建schema2兼容/同签名/code>=10包，勿卸载清数据或装旧低code。

---

# 当前交付 — WalkPaint v0.8 UI（2026-10-09）

本节优先于历史交接。用户要求一个主写手连续完成地图优先的 UI，已从最新 v0.7 高德基线 bffb1d4 继续；唯一工作目录 D:\Projects\WalkPaint，分支 feat/v08-map-first，base issue-2-recording-health。草稿PR：https://github.com/h516df/walkpaint/pull/5，Refs #2/#4，依赖#3/#1，不自动合并。main 和 PR #1/#3 未合并或修改。状态：**UI / 实际 APK / 模拟器验收 PASS；私有 GitHub 下载已开放；真实 vivo 与 Issue #2 原问题未完成。**

- 业务源码 `f127d0a3f7bd0a5c7131655690bba7e1b57043d4`，APK versionCode9 / 0.8 / cn.walkpaint / schema2 / 原签名 / arm64。大小 70344528，SHA256 `28dc325e8005a4cab4219a91b45cf74517318be575820589eaab787ad7dcc197`，本地/模拟器安装相同。私有预览渠道 https://github.com/h516df/walkpaint/releases/tag/v0.8，资产名 WalkPaint-v0.8-arm64-debug.apk；已核对服务器 digest/大小/状态与本地安装包一致，private=true，draft=false/prerelease=true，标签固定业务提交f127d0a；publication.json保存实读。旧 v0.7 与 v0.7-amap-key-test 保留。
- 首页地图、真实道路区间、蓝点、定位按钮、轻薄导航；常驻标题/统计/录制卡/开关/GPS 参考/演示路线全部移除。正常无状态；明确异常才有小提示到我的，首次未获得回调不误说断录。
- 足迹展示累计/今天真实道路、下载区域内进度、每日历史；不拿 GPS 距离/网格当探索。我的管理自动记录、暂停/恢复/撤回、权限/省电、道路下载/导入、备份/恢复、诊断、地图及隐私。高德授权撤回回退合法离线。每日缓存仅刷新变化日期，道路目录变化时重建；最近31天外仍能点选查看，不删除历史。
- 无定位或权限失效时，异步历史加载后一次聚焦已走道路；修复默认北京视角，不覆盖用户后续手动视角。原始 GPS / OSM 为 WGS84，高德仅显示边界转换一次。
- 新增无坐标匹配原因复算，最近2000段按当前目录统计断档、精度、无候选、端点不唯一、跨道路和区间/路口拒绝；明确“复算不等于历史已确认”，不重写库或放宽 matcher。公开失败案例可以稳定出现 GPS 接受而 0 米；无法据此断定真机76段原因。
- 87 项 JVM 和实际构建/原签名通过；Android 隔离数据库、原 172 点/全部行保留到 183、新增匹配3段、后台短时关屏、去重/断档、正常/无可靠定位/权限/回调中断、导航/按天/暂停恢复撤回、320dp 小屏、设置与选择器真实入口全轮通过。实际截图及详细边界见 docs/evidence/V08.md，verification/v08/delivery-final/acceptance.json。最终 APK 改动之后重新全轮，未拿旧包截图冒充。
- 高德 Key 已于 Issue #4 记载在本机忽略配置配置，未读取/打印/提交值；本轮 SDK 实际渲染，CLAUDE 旧“无 Key”已更新。安装包含 SDK 必需的受包名/签名限制 Key，按用户授权只在私人渠道分发；没有私人 GPS/地图截图/真实路线上传。
- 仍未验收：vivo 户外/长期后台、Doze/省电/重启/耗电、中国真实道路偏移；真实79 GPS / 76未确认 / 0米和两小时无入库原因在 #2 保持开放。旧模拟器强停重开失败仍留存，不声称永不断录或用户手机已更新。备份 UI 只打开/取消选择器，完整往返是在隔离库。
- 回退：以 v0.7/schema2 兼容源码构建同签名、code>=9 的修复 APK 覆盖，不卸载、清数据、降schema或直接装code8。用户远程操作，只需手机安装确认/独立授权；不要求 USB 或执行命令。后续 #2 需要无坐标诊断的真实反馈，不私自添加遥测。

---

# 当前交付 — v0.7 私有远程测试下载（2026-10-09）

用户明确澄清“我是远程控制 Codex 的”，不在本机电脑旁。此前直接要求“你不放到 GitHub 私人仓库里面，我怎么下载啊”已授权该项目的私有下载分发；本次沿用会话既有授权提供测试包。先前“本轮未发布”是该时点状态，以下分发事实优先；不把分发当P0验收完成，不合并PR、不公开仓库、不上传私人轨迹。

- 私有测试发布：https://github.com/h516df/walkpaint/releases/tag/v0.7，draft=false/prerelease=true，仓库isPrivate=true；标签v0.7固定已测试业务提交ec0d2b50d9d137f72f4370de71024944298334c4，后续文档不移动标签。
- 手机APK：https://github.com/h516df/walkpaint/releases/download/v0.7/WalkPaint-v0.7-arm64-debug.apk，70348196字节，服务器SHA256与本机/模拟器安装指纹一致：8c9f47d6f9a08d4a9f1e3b5b74b8b9f89be595eb8baa7c63f4bafc9002d5b9dc。cn.walkpaint / code8 / schema2 / 原签名 / arm64-v8a，只覆盖安装，不卸载或清数据。
- 下载只需手机浏览器登录有权限GitHub账号；安装后打开一次，手机系统需要的安装确认/定位授权由本人完成。用户正常外出使用，后续按真实反馈验收。电脑上的构建、检查、上传、代码与诊断分析由Agent完成，不要求用户到电脑旁、USB连接、执行命令或搬日志。无法直接读取手机时诚实保留未验收状态，不自动添加远程控制/遥测/GPS上传。
- 76项JVM/隔离库与MuMu后台/关屏/道路/历史分项PASS；整链重开有一次FAIL且观测系统进程重启，原因未确定；同APK独立恢复/暂停/再保存专项PASS。vivo户外/长期后台/耗电及手机偏移尚未验收，Issue#2开放，PR#3仍草稿。不能宣称原手机断录已经修好。
- 分发先建草稿、上传唯一手机包、核对服务器digest/大小，再启用下载。此前tag REST端点对草稿404，改用发布列表/id读取；正常草稿没有正式标签，不当成权限故障。发布实读 verification/issue-2-p0-recovery/publication.json；原测试、失败与final-audit保持原样，不回写历史结果。
- 当前私有草稿PR https://github.com/h516df/walkpaint/pull/3 依赖PR#1，不合并。回退仍须兼容schema2、同签名、code>=8的修复包，不能直接装旧schema1或卸载降级。

---

# HANDOFF — Issue #2 P0 / v0.7 本地候选（2026-10-09）

本节优先于下方历史暂停与 v0.3–v0.6 文档。用户明确要求读取 Issue #2 并继续 P0，开发已恢复。唯一写手，D:\Projects\WalkPaint，分支 issue-2-recording-health，基线 8bcddeff963529ff7cf9b41242d01df4fd492007（PR #1 的 v0.6 分支）。没有修改/合并 PR #1 或 main。最终运行验收对应代码提交 `ed5f86ecb211f198ede8bb434e3f26b547370c23`，构建之后未修改业务源码。

**状态：软件改进可运行；模拟器后台/关屏/道路/历史分项 PASS，完整整链强停重开处一次 FAIL（调查中观测到 Android 系统进程重启，原因未确定），同 APK 恢复专项随后 PASS；真实 vivo 户外/长时间后台验收 BLOCKED（没有真实设备接入）。用户手机原断录根因仍未确定。Issue #2 保持开放，不能宣称真机可用或完整整链稳定。按本 Issue 的明确限制，本轮不合并、不发布 Release、不上传 APK 或私人足迹。**

## 改动与证据边界

- 先在原 v0.6 复现：模拟 GPS 切后台/关屏仍入库；停发回调65秒，服务仍存活而地图仅提示到室外等待，没有明确中断。verification/issue-2-before/no-callback.png 与 acceptance.json 的 ui_reports_callback_interruption=false、service_alive=true 为失败依据。基线脚本最初误执行了 v0.5 完整验收，仍保留了全部旧行、完成测试清理；其后的专项实验独立记录前台/后台/关屏点。这不是 vivo 杀后台证据。
- 统一地图、个人页与通知的状态：服务、GPS 原始回调、通过时间/坐标/精度的有效 GPS、实际 SQLite 提交分开；暂停/缺权限/系统 GPS 关闭/注册失败/回调中断/差精度/保存失败有区别。最后入库时间不再用历史定位时间冒充。新会话不能沿用上次有效 GPS 宣称恢复。
- 增加本机无坐标证据：有效 GPS 时间/计数、屏幕/省电/Doze 状态、上次无停止理由的会话恢复、Android 自己进程的退出原因码。只读官方 ApplicationExitInfo，不读描述或 trace，不推断 vivo 根因；API<30 无该退出接口。诊断常规30秒合并持久化，SQLite 足迹即时提交；未加 CPU 常驻唤醒锁/新服务/后台上传。
- 地图显示端采用单调时间年龄并优先新鲜可靠 GPS，网络定位不会替换它；回退网络定位不代表保存 GPS。记录与匹配阈值、坐标系均未变。旧界面渲染失败时还有道路歧义因素，因此不把该改动说成已经证明解决用户手机的偏移。
- 足迹按本机日期汇总点数、记录段与轨迹距离，短途显示米数；保持原 trip ID/SQLite 行、各断点，不桥接零散路线。点一天可看当天 GPS 参考和仅该天已确认道路区间；只读重建来自 road_matches=1，返回累计地图恢复全局覆盖。当天累计去重与跨天总覆盖不能相加冒充总里程。
- 当前附近无目录道路时提示边界，已存数据保留，不自动下载。没有增加整城下载、自动上传、移动模式分类或未授权高德 Key。

## 最终候选与测试

- cn.walkpaint / versionCode8 / versionName0.7 / schema2。手机 APK WalkPaint-v0.7-arm64-debug.apk，70348196 字节，SHA256 `8c9f47d6f9a08d4a9f1e3b5b74b8b9f89be595eb8baa7c63f4bafc9002d5b9dc`。apksigner 验证通过，证书 SHA256 21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967，与 v0.6 相同；安装包仅 arm64-v8a。
- 76 项 JVM 测试零失败，包含服务活性不能掩盖回调断档、有效回调不等于提交、新会话/差精度/保存失败、GPS 与网络优先级、日期/午夜/原断点和目录外不乱涂。
- Android 隔离库回归 PASS：schema1→2 原行/ID、备份去重、道路坏包与部分覆盖写入失败不丢 GPS、原 GPS 写入失败仍明确报错；新增当天覆盖读取不改变原表及累计覆盖。无故障注入到实际足迹库。
- 最终实际 APK MuMu/API35 真实 UI：同签名覆盖升级时验证 121 原点与全部 trips/points 行保留；恢复专项另以 132 原点和全行作基线，最后 133 点、原行全部保留。系统权限弹窗授权后服务恢复；后台与关屏 70 秒中持续注入公开测试定位并保存；新增 4 个确认匹配，反向重走不重复累计；地图青绿线真实渲染。
- 差精度拒绝、停发回调65秒而服务存活时显示“GPS回调已中断”、恢复不跨45秒断档涂色、目录外异常跳点拒绝并提示边界、每日列表→当天地图→累计地图、强停后手动打开恢复、暂停不写与再次明确启用均通过。测试是模拟器公开位置，不能当真实走路。
- 最终已安装 base.apk 指纹与上述候选完全相同；测试 provider、Mock Location、原权限、旋转、位置开关和临时文件恢复，cleanup_errors=[]。verification/issue-2-p0-complete/ 保存整链失败与此前分项证据；verification/issue-2-p0-recovery/ 保存独立恢复/暂停/再入库 PASS、diagnostic-fields.json 和真实截图，不能把两者混成一次全通过。禁止把私人轨迹随证据上传。
- 首轮 v0.7 界面验收在返回地图时失败（没有可见青绿道路），verification/issue-2-p0/ 保留失败与清理结果。只读 catalogProbe 证明新增周边目录使原测试4米偏移点出现歧义；道路中心点可无歧义匹配。最终测试据此采用道路中心，且强制检查新增匹配行，防止只凭旧42米覆盖假通过。没有放宽 matcher。catalogProbe 仅固定公开 fixture、读取目录、输出候选数量与布尔值，无坐标/路线输出、无写库。
- 第二次在诊断弹窗检查因全部内容已显示、没有可滚动节点而使脚本StopIteration（verification/issue-2-p0-final/）；这是脚本判断错误，保留已通过项与清理结果。修正为内容完整则无需滚动。
- 随后的整链验证在强停重开时没有前台服务（verification/issue-2-p0-complete/）。调查观测 Android system_server 从 PID841 变为17136；无已确认的 Java 崩溃证据，重启根因未确定。再次打开后服务恢复；同一 APK 独立恢复专项再次强停/可见重开，在 system_server PID 不变时 PASS，暂停0新增、明确启用后真实入库1点到133。这是分项与恢复复验，**不能说完整整链已经稳定通过**。scripts/android_p0_recovery.py 保存原失败来源及专项边界。

## 未完成与下一步

- 真机 = BLOCKED-no-real-device-connected。MuMu 型号虽回报 V2284A，127.0.0.1:16384 仍是模拟器，不能写成 vivo 真机。实际卫星环境、长期锁屏、Doze、厂商自启动/省电、重启自动恢复、耗电及用户截图偏移尚未验收；不能保证永不断录。整链重开期间的系统重启原因也尚未定位，需要继续保留为本 Issue 未完成项。
- 在获得真实设备的最小连接/调试授权后，继续读取无坐标诊断、覆盖安装同签名包、实际户外后台/锁屏时段验证持续入库、青绿道路和历史保持。必须人操作的物理连接/授权再请求，不把用户当命令执行器。
- 高德合法 Key 仍缺失；保留现有合法离线/联网底图与单次道路下载同意。手机 v0.6 下载链接继续可用，本轮新包仅本机。
- 回退：不卸载/清数据，不直接装 schema1 或降 versionCode。由 v0.6 源码构建同签名、schema2、versionCode>=8 的修复包覆盖安装；已有 v0.6 资产与历史提交均保留。

官方行为依据：[前台定位权限](https://developer.android.com/develop/sensors-and-location/location/permissions)、[后台启动前台服务限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo)。这些是系统边界，不能替代真机证据。

---

# HANDOFF — v0.6 最终本地包验收通过（2026-10-09）

> 本节为当前状态，覆盖下方历史未复验/窗口授权/网络恢复阻塞。唯一写手，D:\Projects\WalkPaint，feat/v06-nearby-roads；基线6b9f8d3，下载功能提交1096926，地图修复/收尾提交见git log。**最终手机APK在MuMu真实入口验收通过，现已推送代码并发布到私有GitHub；未安装到用户真机，不能说用户手机已生效或后台已保证。**

## 当前产物

- 手机包：[WalkPaint-v0.6-arm64-debug.apk](WalkPaint-v0.6-arm64-debug.apk)，70311554字节，SHA256 `c308e9f11c7540e42753e8bba5c1bb61d1c57dbb48147a01e13b2ed83a235300`。cn.walkpaint/code7/name0.6/schema2，ZIP仅arm64-v8a。
- 同源码通用包：[WalkPaint-v0.6-universal-debug.apk](WalkPaint-v0.6-universal-debug.apk)，SHA256 `ba6f144431161b3bdb8caaac67df0c850768a22d97804f679bbca1ecd5287ff4`。通用包已构建，最终运行验收采用手机arm64包，不能把ABI不同的指纹混用。
- apksigner成功，旧证书SHA256 21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967未变。只覆盖安装，不卸载、清数据或降schema1。

## 本轮完成与准确证据

- 应用内单次同意区域/IP后下载附近约1.2公里小区域的完整OSM道路；不上传完整足迹、不自动更新/重试/换主机。下载文件仍走已有8MB/2000way校验和原子本地导入、历史重匹配。只使用overpass-api.de；底图联网授权独立。
- 从真实渲染发现“查看地图”选择第一条catalog道路，把涂色移出视口；已修为新鲜当前位置→最近真实足迹→已有覆盖/导入几何，不以演示点代替真实位置。RoadMapFocusTest覆盖当前定位、最新真实轨迹、空数据和导入几何后备。
- 当前源码**59项JVM回归，0失败**；包括完整/不完整道路响应、网络/HTTP/限额/预取消、定位新鲜度和地图聚焦，以及原GPS/道路算法。离线testDebugUnitTest/assembleDebug/assembleDebugAndroidTest通过。
- **最终手机包**真实UI下载/确认取消/重复去重/重开保留通过，verification/v06-final/acceptance.json，V06_NEARBY_ROADS_UI_NETWORK_PASS，SHA对应上述c308e9f1。1980道路重复下载仍1980，原GPS全行保留，累计约42米。人工查看downloaded-road-map.png，蓝点、灰路和青绿覆盖正常。
- **同一个最终手机包**覆盖升级和最后定位/地图smoke通过，verification/v06-arm64-complete/acceptance.json，V05_FINAL_APK_SMOKE_PASS：89旧点和所有trips/points行保留，新增一个明确模拟GPS到90；真实文件选择器导入、数据库隔离迁移/故障隔离/备份去重、前台服务、新定位保存、状态文案和地图青绿3392像素验证通过。没有重复旧版长断档链路或真机后台试走；准确边界保持。
- **安装指纹和清理实读通过**：verification/v06-delivery/final-audit.json；读取安装base.apk SHA完全等于最终手机包；code7，90点、1980路，integrity_check=ok，shell Mock Location=default，测试cache/prefs=0，Wi-Fi=1。没有用户数据导出或手机操作。

## 排障记录和限制

- 初版断网测试切断MuMu自身ADB，finally未完成；原失败证据保留在v06-nearby。后来发现模拟器进程已经关闭，官方管理器只启动索引0后，窗口/ADB恢复；不再需要用户窗口授权。网络与模拟定位清理实读已完成，不把“发出恢复命令”当结果。v06脚本已移除切断模拟器网络的步骤。
- 最终下载复验曾出现IOException，先保留失败证据，再通过隔离Android网络探针确认同生产客户端可返回1980条道路，并记录仅异常类型/原因类的安全日志（不含经纬度、正文、凭据）。之后同最终界面成功下载两次并通过重开。**连接失败根因未确认，不归因于模拟器、服务或时序；没有修改用户电脑代理/网络，也未更换生产服务。**
- Android网络探针仅在测试APK明确networkProbe=true时请求固定公开Oxford区域，并读取公开服务状态；不导入、修改足迹，不从真实GPS选区域，默认数据库隔离回归不会触发联网。它不能代替UI证据。
- 真实断网UI、过期定位UI未通过；已用确定性传输/定位边界测试验证逻辑，标明未测。初版模拟器下载成功不证明用户国内网络可达；OSM缺路/服务繁忙仍可能少涂或下载失败。没有全球/整城自动下载、高德Key、复杂道路真机误匹配或长期规模保证。
- 真机未连接、未安装；户外定位、vivo省电/Doze/杀后台/重启和耗电等待实机证据。由Agent继续处理可自动完成的步骤；用户只需下载/安装确认、位置授权及必要的实际走路。

## 下一步与回退

用户随后明确要求放进私有GitHub供下载，分发授权已获得并执行。代码提交a81063d推至feat/v06-nearby-roads；草稿PR https://github.com/h516df/walkpaint/pull/1 未合并。v0.6标签固定a81063d，私有预览发布 https://github.com/h516df/walkpaint/releases/tag/v0.6；draft=false，仓库isPrivate=true。手机APK资产uploaded/70311554字节，服务器digest与c308e9f1验收包完全一致。直达手机下载 https://github.com/h516df/walkpaint/releases/download/v0.6/WalkPaint-v0.6-arm64-debug.apk 。私有访问需手机浏览器登录有权限账号。

首次并发上传两包时通用包HTTP408且两资产未保存；查实际为空后仅对gh子进程使用HTTP/1，单独上传手机包成功。通用包保留本机，不虚称已上传；备用ZIP同APK内部指纹，仅本机保留未发布。发布实读证据 verification/v06-delivery/publication.json。发布后文档提交另行push，不移动已验收v0.6标签，不强行合并main。旧APK/源码和前轮指纹产物保留；必要时从父提交构建schema2兼容、同签名、code>=7的修复包，不通过卸载/清库降级。

---

# HANDOFF — v0.6 附近道路下载（历史：恢复前状态）

> 用户通过截图明确要求接手并继续做到正常使用。本节覆盖旧“已暂停”等状态。唯一写手；D:\Projects\WalkPaint；任务分支 feat/v06-nearby-roads，从已核对的干净提交 6b9f8d3 继续。本地提交，不 push、不发布、不操作用户手机。**状态：本地安装包已生成，真实下载初版通过；最终安装复验和测试状态清理待模拟器网络/窗口授权恢复。不能称最终交付或用户手机已经可用。**

## 改动

- 应用内「下载 / 查看附近道路」：取得5分钟以内、精度1–200米的当前位置，中心约100米取整，说明向 overpass-api.de 发送区域/IP 后，用户选择「同意并下载本次」才 POST 一个约1.2公里边长的小区域查询。无完整足迹上传、自动联网、重试循环、第三方备用主机或新增服务。仍可导入本地文件；底图联网另行同意。
- 读取完整 OSM way/version/geometry，不裁剪输出，不保存服务返回的用户名等编辑者元数据。8MB/2000 way、25秒服务查询限制、HTTP/读取超时、取消/关闭页面断开、禁止自动重定向。复用原道路文件校验和原子导入，重新匹配历史轨迹，不改 GPS/数据库结构或过滤规则。
- v0.6 code7、schema2、旧签名；来源与许可 OSM ODbL。每包/累计上限沿用原实现；区域太密、OSM缺路或服务不可访问仍可能无法下载/少涂，不是完整城市下载器或全球道路覆盖。

## 产物和证据边界

- 手机 arm64 包：[WalkPaint-v0.6-arm64-debug.apk](WalkPaint-v0.6-arm64-debug.apk)，70106515字节，SHA256 `e786348459b0bd7c5d9335a7107c2df652abe56761106de39a4116cb2b096643`。通用包：[WalkPaint-v0.6-universal-debug.apk](WalkPaint-v0.6-universal-debug.apk)，SHA256 `a105913126f32d9ee0ae83226452e3a95f9ac32d5d2055ecaca8515c30343e12`。手机包 ZIP 只含 arm64-v8a；apksigner验证成功，证书与原版一致。**两个最终文件尚未覆盖安装复验**。
- 当前源码55项JVM测试通过，0失败；包括完整way/缺几何/超时部分响应/无路/无版本、区域/定位新鲜度、HTTP429/重定向拒绝、断网IOException、8MB限制、预取消零请求和连接关闭。均为逻辑/传输边界测试，不能冒充真实断网UI结果。离线 testDebugUnitTest/assembleDebug/assembleDebugAndroidTest 成功；之后生成手机包。
- 初版 v0.6 APK `ac4e99c71b4dd7dbe906fd20bce1c8f7500e77075d171f71bbc31318121126a6` 在原MuMu Android15/API35覆盖安装：84旧点及全 trips/points 行保留，新增1点到85；原系统文件选择器、Android隔离升级/道路故障隔离/备份回归通过，地图青绿3442像素。verification/v06-upgrade/acceptance.json。**沿用此前数据库可靠性修复，本任务未重写。**
- 同一初版真实App下载公开Oxford测试区域成功：6→1980条道路，85旧点保留、新增一个明确模拟器定位点到86；再次下载相同道路不重复，强停后重开道路/足迹保留。真实UI同意/取消/加载/结果/地图截图见 verification/v06-nearby。人工查看下载结果及加载截图。道路更新后回算约42米，与旧42.643米略不同，是最新几何/邻路候选重新匹配结果，原GPS保留。测试不发送用户私有位置。
- 本轮最后改动只增加可注入HTTP传输的测试边界、复用等价定位新鲜度函数及取消前检查；源码和最终APK新指纹如上，**没有假称其重新通过真实UI验收**。

## 真实阻塞和待恢复状态

- 真实断网测试调用 `svc wifi disable` 后，MuMu自身ADB也断开，随后 `svc data disable` 超时。finally中的网络/测试provider/Mock Location/旋转/临时XML恢复均无法经ADB完成。verification/v06-nearby/acceptance.json保留已通过项和5个cleanup_errors；**整份测试不是通过状态**。后续脚本已移除切断模拟器ADB的网络开关，断网/HTTP失败走确定性传输回归。
- 已用官方MuMuManager确认唯一索引0对应127.0.0.1:16384，并只重启这个设备；没有重置、删除或卸载。官方管理器显示Android启动完成，但ADB仍offline，不能据此宣布恢复。恢复前后点数尚未复读。
- Windows computer-use工具准确选中唯一「MuMu安卓设备」窗口，但在首次get_window_state等待本机应用控制授权超时，未取得窗口截图、未点击设置。已向用户提出最小授权请求，仍待回应；不绕过授权或盲点坐标。启动的MuMuManager重连循环PID22820已按实际进程/开始时间核对并停止，不停止共享ADB服务。
- 恢复步骤由Agent完成：获得该窗口授权→查看实际安卓网络设置，恢复测试前开启的Wi-Fi→ADB重连→核对并恢复shell Mock Location至此前默认，检查测试provider/定位/旋转和临时XML，记录实读结果→按 `-r` 安装最终包、核对原行和地图/GPS→运行无断网开关的scripts/android_v06_acceptance.py。不要让用户执行命令或搬日志。
- 真机未连接、未安装。实际户外、vivo后台/Doze/重启/耗电、国内网络可达性/OSM道路完整性仍未验收。用户手机版本不能据模拟器结论宣称生效。

## 保护和回退

只覆盖升级，不卸载、不清数据、不降到schema1。初版和最终通用APK均保留；父提交6b9f8d3支持schema2，必要时从它构建code>=7的兼容修复包回退，先验证签名/数据。APK/日志/坐标/测试数据库不进Git。私有GitHub发布下载链接须获得用户明确发布许可；当前没有上传或发布。

---

# HANDOFF — v0.5 道路故障隔离修复（2026-10-09）

> 本节为当前交付，优先于下方旧 APK 和历史暂停记录。同一会话、唯一写手、D:\Projects\WalkPaint、feat/v05-real-road-coverage；修复基线 1c46f5ae3d274577e46f1321c922f458dff96a71。仅本地提交，不 push、不发布。最终提交见 git log -1。

## 最小修复

- 已接受 GPS 先在原事务写入，paintPair 单独使用 SAVEPOINT。道路 catalog / matcher / 匹配行 / 覆盖区间失败时，ROLLBACK TO 完整撤销道路写入，再释放 savepoint 并提交 GPS；不会留下半个匹配记录或半个累计区间，也不会设置永久 GPS lastError。失败的匹配保留未确认，重新导入合法道路包回算全部原始 GPS。
- SAVEPOINT 创建、回滚、释放失败不吞掉，仍进入原始 GPS 写入失败路径；原 points insertOrThrow 及 lastError 行为保留。检查 Android insertWithOnConflict 的 -1 返回，避免道路写入错误被静默当成功。schema2、versionCode6、GPS 阈值及原签名不变。
- 道路读取失败返回明确的道路错误状态，让界面仍能刷新原始 GPS。有效道路重导入可替换同 ID 的损坏几何，并在整个导入事务成功后清除道路错误。没有迁移、清库或更改原 trips/points。
- 可选底图同意持久保存在本机：默认 false，只有明确选择“同意联网并记住”才会加载 OpenFreeMap，文案说明今后打开会联网以及视口/IP 风险；“使用离线道路”持久撤回。没有自动许可、足迹上传或公共瓦片道路采集。

## 当前产物和验证

- 当前 [APK](app/build/outputs/apk/debug/app-debug.apk) SHA256：ada9714cfbeedceb754aac4d6cff6ce089d4cae46443d4d242c709dde3e07744。apksigner 验证通过，证书 SHA256 21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967，与 v0.4/v0.5 原签名一致。
- JVM 41 tests / 0 failures / 0 errors；testDebugUnitTest、assembleDebug、assembleDebugAndroidTest 离线构建成功。日志 verification/v05-reliability/gradle.log；原有单测不变。
- **先复现再修复**：新隔离 instrumentation 在旧 APK 8c75e085... 上失败，明确报 road catalog failure discarded accepted GPS，记录 verification/v05-reliability/regression-before.txt。未修改实际足迹库。
- **最终 APK Android 隔离回归通过**：使用 UUID cache 数据库和有源许可的真实 Oxford 道路。损坏 geometry JSON 后两次 GPS 均保留，lastError 为空，道路错误可读，原 coverage/matches 不变；有效重导入恢复已遗漏覆盖和清除道路错误。coverage INSERT trigger 在 road_matches INSERT 与旧区间 DELETE 之后报错，验证两次后续 GPS 均保存，整个道路状态保持原样；撤掉故障后有效重导入恢复。points INSERT trigger 则明确返回 false、lastError 非空、没有新增 raw 行或覆盖。证据 verification/v05-reliability/database-tests-final.txt，包含 accepted GPS survives catalog and partial coverage failures / valid reimport recovers / raw GPS write failure remains fatal。
- 原 v1 行/ID 升级、备份往返、重复导入、integrity_check 和测试 cache/prefs 清理继续验证；故障注入仅针对隔离测试库。原实际库没有故障 trigger、坏道路或测试数据库替换。
- **最终 APK MuMu 真入口 smoke 通过**：仅 127.0.0.1:16384，Android15/API35，覆盖安装 -r，实际系统文件选择器重导入道路。83 个原点及所有 trips/points 全行保留，新增1个明确模拟 GPS 到84；实际地图道路覆盖42.643米，3442个青绿道路像素，人工查看 [最终地图](verification/v05-reliability/final-apk-map.png)。记录 verification/v05-reliability/acceptance.json，V05_FINAL_APK_SMOKE_PASS，cleanup_errors=[]。此次没有重复完整45秒长断档链路，沿用下方原 v0.5 完整链路证据及未改的 JVM 边界测试。
- 收尾实读已安装 base.apk 的 SHA256，与当前构建 ada9714c... 完全一致；再次核对84点和全行保留。实际点击“使用离线道路”，强停后重开，确认本机同意标志为 false 且页面显示“本地离线道路”；人工检查 [同意说明](verification/v05-reliability/basemap-consent-dialog.png) 和 [重开后的地图](verification/v05-reliability/final-after-regression-map.png)。final-checks.json 记录通过、测试库/prefs 无遗留及 MOCK_LOCATION=No operations / Default mode: deny；没有保留模拟定位授权。测试 provider 和旋转设置已恢复。
- 收尾脚本两处检查问题已纠正：返回 App 仍在“我的”页时不能直接点地图道路图例，改为真实强停重开并选择地图；MuMu 未授权 mock 的返回是“No operations / Default mode: deny”，不能要求输出小写 default。故障回归和首轮最终 APK smoke 已成功，两处均未改变产品代码、旧数据或覆盖；改完后只补核对失败的收尾项。

## 限制与回退

仅 Android15/API35 模拟器运行验收。兼容旧 Android SQL classifier 的前导空语句 ROLLBACK TO 已在当前模拟器验证；Android8/API26 等旧系统未运行验收。真实用户手机未操作、未升级，户外定位与 vivo 长期后台未验收。联网同意的正向加载未点击验收，整个测试仍为离线，未发送私人浏览区域。回退可从修复前提交构建 schema2 兼容 APK；不要卸载、清数据或降为 schema1 APK。

---

# HANDOFF — v0.5 真实道路涂色首次交付历史（2026-10-09）

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
