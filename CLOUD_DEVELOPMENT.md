# 云端开发 · walkpaint

自 2026-10-21 起，后续源码开发、测试与构建默认在云端执行。

[在浏览器创建开发工作区](https://codespaces.new/h516df/walkpaint)。选择本仓库的开发容器；首次创建会安装固定主版本工具与项目依赖。用完停止工作区，避免持续计费。也可以在 Codex 云端拉取本仓库，运行 `bash .devcontainer/setup.sh` 后使用同一检查入口。

```bash
source .cloud/env.sh
bash cloud/check.sh
```

GitHub Actions 的 **Cloud development** 在 PR、main 推送及手动运行时检查。通过结果绑定当前提交；构建和模拟器通过不能替代实体手机验收。依赖与缓存可重建，代码必须提交到分支并推送，临时工作区不是长期资料备份。

## 当前边界

云端只构建 cn.walkpaint.dev；未配置地图 Key 时不验收地图加载。原正式签名、真实 GPS、户外/锁屏耗电与覆盖升级仍需单独验收。

原 README、AGENTS 与交接文件中的 Windows 路径作为历史恢复线索保留；云端开发不依赖这些路径。生产运行与发布步骤按本节边界处理，不自动运行旧的本机派发、安装、隧道或签名脚本。凭据通过平台 Secrets 注入，不能进入源码、日志或构建附件；私有项目继续保持私有。

## 配置依据

- [Dev Container 规范](https://containers.dev/implementors/json_reference/)
- [GitHub Codespaces](https://docs.github.com/en/codespaces/overview)
- [Android 命令行工具](https://developer.android.com/tools/sdkmanager)：固定工具包版本 20.0 并核对 SHA256，SDK 36 / Build Tools 36.0.0；JDK 21，兼容模块另装 JDK 17，Python 3.12。

## 会话接续

### 新会话从哪里开始

本仓库内的约定和已推送的文件是接续依据。新会话不会自动继承旧聊天、未推送改动、上一台云机器的临时工具或登录态。

1. 读取根目录 [AGENTS.md](AGENTS.md) 与 [HANDOFF.md](HANDOFF.md)，再读已有项目规则和任务相关文档。
2. 核对远端 main、当前分支、工作区状态与相关 PR。HANDOFF 里的历史分支/测试结果需和 GitHub 当前状态逐项核对，不能直接当成最新状态。
3. 如果上次工作尚未合并，打开交接指定分支或 PR；不要只取 main 后重新实现一遍。一个分支对应一个进行中的任务，共享文件避免并发覆盖。
4. 工具链缺失时按本文现有初始化方式恢复；业务验证继续使用现有检查入口。不要自动运行历史恢复、生产部署、发消息、签名或发布命令。

### 结束前必须保存什么

- **长期约定**：更新 AGENTS 或已有项目规则，区分用户明确要求与代理建议。
- **当前进度**：维护 HANDOFF 顶部“当前接续记录”，替换过时状态，保留下面原有历史和证据链接。不同任务的未合并进度留在各自分支/PR，合并时整合当前记录。
- **交接最少字段**：当前目标；已完成和待完成；仓库与分支/PR；基线或验证提交；运行/测试命令及 passed / failed / not run；所需配置的名称与来源；阻塞；下一步。
- **远端副本**：提交本任务源码与交接，推送独立分支，再核对 GitHub 上的提交 SHA。PR 尚未合并也可继续，但必须告知准确分支/PR；不要仅保存到旧会话的磁盘。
- **交付回执**：给用户仓库、分支/PR、远端提交 SHA 与下一次可直接粘贴的续接提示。远端未确认前不能说“可以删除旧会话”。

只写配置的键名与申请位置，不写值、个人资料或私人日志。未推送的文件、任务 VM 和 Actions 临时附件不作为唯一长期备份。

### 环境与凭据

本仓库的开发容器和初始化脚本用于恢复工具链；它们不会自动把当前任务变成所有会话共用的机器。若需新 Codex 任务自带已安装工具，必须在 Codex Cloud 环境的编辑流程中准备、验证并 Publish / Republish；仓库提交不等于环境已发布。

密钥通过所选环境的变量/Secret 配置提供；GitHub Actions Secrets、Codex Cloud 凭据和旧电脑登录态不是同一处配置。本轮只建立仓库接续方式，没有读取或迁移凭据，也不声称已修改 Codex Cloud 的已发布环境。

### 给新会话的提示

> 请继续这个仓库的工作。先读 AGENTS.md、CLOUD_DEVELOPMENT.md 和 HANDOFF.md，核对远端 main 与交接指定分支/PR，简短说明已知进度和下一步，再继续未完成任务。不要把历史测试或未合并候选当作当前主线；把本轮进度与代码一起保存并推送。

如果要继续尚未合并的任务，在这段话前补上旧会话给出的分支或 PR 链接。

参考：[Codex Cloud 的环境复用和任务保存机制](https://learn.chatgpt.com/docs/environments/cloud-environments#reuse-and-update-saved-state)。

## 通用工作流入口

通用约定与模板统一维护在 [vibe-ui-quality-kit/workflow](https://github.com/h516df/vibe-ui-quality-kit/blob/main/workflow/README.md)。开始任务由代理主动读取；本仓库 AGENTS 内保留可独立执行的简版，跨仓库读取暂不可用时先沿用，不要求用户重复说明工作流或添加仓库。具体工具链、数据、签名、生产与设备保护仍以本项目约束为准。

新任务从默认分支开始时，代理应按用户描述核对相关未合并 PR、依赖和对应分支交接，不能直接把 main 当作所有最新成果；不根据旧快照自动合并业务 PR。这里的仓库配置不代表 Codex 账户级环境已发布。

## 2026-10-22 原签名准备

当前准备分支见 [SIGNING_PREPARATION](docs/SIGNING_PREPARATION.md)。历史未迁移状态由本轮实际运行结果更新；PR 未合并不代表 main 已启用。
