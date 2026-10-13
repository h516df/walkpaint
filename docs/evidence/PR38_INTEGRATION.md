# PR #38 冲突整合与旧入口清理

2026-10-13，用户明确允许此前因审批限制未执行的整合和相关旧脚本清理。只在 `agent/codex/amap-v042` 自有 worktree 合入固定提交 `a22f94ef762b1158eb955e69bb0d4ee4b13d06e2`；目标分支为 `agent/codex/mobile-ui-redesign-v041`，不连带合并历史 PR 栈。

四处冲突保留 v0.43 最新事实，新增 `UiStateProbe` 并保留高德运行、手势、中国夹具、像素和数据探针全部入口。发布工具保留草稿恢复、逐项资产核对和 `--verify-only`；大文件上传运输问题仍由 Issue #39 跟踪。本次没有调用远端上传或修改发布。

八个旧文件在确认不属于已发布 v0.41 基线、且没有在用入口引用后，逐项复制并核对 SHA256，再移出当前树。备份目录为 `D:\Workspaces\Codex\2026-10-13\xai\work\preserved-dsh\historical-delivery`，清单见 [obsolete-files.json](pr38-integration/obsolete-files.json)。Git 历史仍保存原内容。

验证结果：

- 16 项发布恢复/UI 结果断言 + 11 项高德产物/专用设备守卫，合计 27 项 Python 回归通过；CI 纳入两组测试。
- `:app:assembleDebugAndroidTest -PwalkpaintArm64Only=true` 成功，53 项任务、5 项执行。只构建验收包，没有重新构建或安装主 App。
- 新验收包 SHA256 `854e4f06d71963024f65ebe7319e094f2b2dfc4e7c2cbaaf015a8c950f0005cb` 在专用 Google AVD 的原正式包上运行；新 `UiStateProbe` 与原高德探针均通过。实际高德 SDK 11.3.100 ELF 映射、加载回调及安装包摘要匹配。见 [probes.json](pr38-integration/probes.json)。
- 本轮 AVD 数据基线是 1 行程、7 点、6 条 road_matches；五表前后行数和逐行摘要全部保持。它与前轮的 7 行程/4,865 点密度测试集不同，因此本轮只证明合入探针和当前数据保留；高密度 UI 证据仍绑定原 v0.43 正式 APK，见 [V043](V043.md)。没有把两个数据状态当成同一基线。
- 原发布入口实际只读回读 v0.43，`read_only=true`；原提交、标签、签名、包名/code45、ARM64、非调试状态和四项服务器资产大小/摘要均匹配。见 [publication.json](pr38-integration/publication.json)。
- 相对正式标签 `v0.43`，`app/src/main`、App/根构建配置、版本与依赖均无变化。正式标签仍为 `8a2d18097c7ce5b9cb00050457b202bc45d981f6`，APK SHA256 仍为 `b8aa5e7479a925e109dc2e544ee186c5acd7addfd575be6e2cf64c2a80e4c5a2`。

用户 vivo 真机、厂商后台、真实 GPS 与本人更新授权/系统安装仍未验收。本轮未操作手机、未修改地图同意或定位权限、未清库或卸载 App。工具可通过 Git revert 回退，旧入口也可从本地备份或 Git 历史取回；不回退或覆盖正式安装包。
