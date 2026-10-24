# v0.70 · 下载恢复与连续回看

用户在0.69浏览器下载末尾失败后，明确要求继续改进。本轮基线550705b04bd6117e61387d4edeaacb0dac72bf9b，分支codex/walkpaint-v070-20261024。交付沿同一GitHub仓库、原签名、递增code78/0.70与schema3。

## 采用与边界

- 应用内现有下载在同一次任务中已有5次Range重试，但全部失败会删除片段；本版以固定pending.apk与有界pending.json保存未完成内容。新进程只做本机清理和提示；用户重新检查新版、确定版本/大小/摘要/文件名一致后才可续传，不保存URL、令牌、账号或远端ID。取消清除片段；不匹配、越界或损坏缓存会丢弃，完整下载后仍走SHA256、包名、版本、minSdk、ABI与原签名验证，不能把片段当安装包。
- 保留现有PR111已进入main的UpdateSession/ReadyUpdateCache与安装确认流程，没有再次合并整个旧业务分支。本轮没有新增权限、后台服务、网络目的地或安装绕过。
- 使用AndroidX SaveableStateHolder仅保存足迹页的轻量UI状态；地图SDK仍保持存活，其他页面路径不改。列表状态上提，使详情返回保持位置；详情可以跳到更早/更晚的真实记录日，空月份可直接查看最近足迹。原始轨迹、GPS过滤、揭雾和SQLite不改。
- 本版改善应用内下载，不控制手机浏览器，不能据此宣称已修复用户浏览器的网络中断。也不保证缓存不被系统回收或无限后台常驻。

## 资料与取舍

- [AndroidX SaveableStateHolder官方实现与说明](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime-saveable/src/commonMain/kotlin/androidx/compose/runtime/saveable/SaveableStateHolder.kt)：用于离开组合树后恢复rememberSaveable状态，保持现有Compose技术栈；只使用公开API，没有复制外部实现。
- [MDN Range requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Range_requests)：沿用206与Content-Range检查、完整大小和SHA256绑定；服务不支持续传时拒绝追加并重新下载。本轮不增加未知镜像或忽略校验。
- 用户截图中的0.69发布文件，上一轮在云端实际完整下载及尾部Range206检查均通过；这只能说明发布文件/该网络路径正常，不能替代用户手机网络验收。

## 验证与发行

本轮结果完成后补录，历史结果不重标成本版通过。场景包括网络失败保留/重建缓存后续传、版本/摘要/大小变化、损坏片段、取消、空间预算、跨页/地图/状态恢复、详情返回滚动、小屏大字及实际渲染。测试只使用合成路线、元数据与内容。

正式包仍由受保护的Original signing readiness构建和签名，再由Publish verified signed release使用同一产物发布；所有附件实际下载读回。实体手机覆盖安装、真实高德联网、户外GPS、锁屏耗电单列not run。
