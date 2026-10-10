# v0.15 过滤诊断候选 — 实际 APK/UI 验收

2026-10-10。Refs #10/#6；从私人诊断分支 ea691b0 继续，业务提交 b9e8a2b4fd28338bbc4101c6ff21719769098baa。独立目录 D:\Workspaces\Codex\2026-10-10\ji-x\work\worktrees\codex-diagnostic-v014\codex-diagnostic-acceptance，分支 agent/codex/diagnostic-acceptance。原目录未修改。

## 本阶段范围

将已实现的五类 GPS 过滤计数及30分钟/2小时历史窗口完成版本区分、真实 UI 验收和同签名手机候选构建。version0.15/code17；旧v0.14/code16已分发，不能复用相同版号。非法版本元数据测试改为显式修改 JSON 字段，避免每次升级因为硬编码旧版本而失去负例覆盖。旧数据、schema2、记录服务、GPS/matcher门槛、地图简洁布局和隐私边界保持。

高级诊断与反馈均有独立分类，跨窗口桶单列、旧记录缺失明确不可回溯。五类分组和窗口算法来自ea691b0，详见V014_DIAGNOSTICS.md；本轮没有新增可信速度桶、GPS/道路/渲染全阶段窗口统计，不把本阶段当#10全部完成，也不宣称#6行车涂色已修复。

## 验证

- 180 JVM / 32 suites / 0 failures / 0 errors；7 Google AVD guard tests；通用、AndroidTest及arm64离线构建成功；git diff --check通过。
- 实际设备守卫确认 emulator-5556 / WalkPaint_Google_API36 / boot_completed=1。仅专用Google，未操作用户MuMu或手机。v0.14 code16→v0.15 code17同签名覆盖；实际安装SHA与通用包一致。
- 高级诊断、复制诊断、问题反馈→道路涂色→预览→复制→滚动至末尾；320dp重新进入预览、滚动、复制、返回均通过。检查了实际截图/DOM；没有只看源码宣称UI完成。
- 测试使用无位置的可逆诊断偏好fixture：1088回调/有效、63保存、1025过滤；分类900/100/15/5/5。它验证显示和口径，不能代表用户这趟驾驶的实际分类。窗口边界20次单列；两小时包含该桶。算法事件分类/脱敏/重载由180项中对应单测验证。
- 五表全部逐行相同：trips3/points19/roads6/road_matches16/coverage1。无GPS注入、定位授权变更、数据库写入或网络授权；诊断/反馈偏好逐字节恢复、视口恢复、临时XML移除、服务停止。专用AVD测试结束停止。
- 保留初次冷启动System UI无响应、剪贴板浮层拦截点击、改变视口导致Activity重建的失败/部分通过记录。调整测试等待系统浮层及重新从真实入口进入后，最终完整诊断链在相同生产APK上通过。没有把旧截图或失败当最终结果。

## APK与边界

70288416 字节，手机 SHA256 2b18acd1917b7d199cb9405fb97d0df5b321687c3c1c2e571b9166afeecfec89；通用 SHA256 c7b023025f48630e2e97a8c3c67e46de0f035a64f3c68f7fd7a4ff91ff9231be。两包dex/release-notes逐字节一致；手机仅arm64-v8a。原证书21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967，apksigner通过。手机包在本chat outputs可下载；候选尚未发布私有Release或安装vivo，无CI，PR不自动合并。

完整原始证据在忽略提交的verification/diagnostic-google/、各build日志及失败JSON；公开截图仅专用模拟器合成计数。交付清单在本chat outputs/v0.15-验收记录.json。测试启动HEAD可能早于业务提交，但运行源码/安装包后未改变；通过实际APK哈希与业务提交对应记录，脚本/文档收尾不修改运行代码。

回退需同签名/schema2/code>=17兼容包覆盖；不能卸载/清数据或直接降版号。真实vivo行车/长期后台仍待验收；#6/#10保持开放。剩余诊断规格继续在已有#10中，不新建重复Issue。
