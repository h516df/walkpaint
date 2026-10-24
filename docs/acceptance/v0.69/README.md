# v0.69 验收与取舍

本轮用户要求：改进足迹涂色并发布可安装新版。基线 main ff6bddd，任务分支 codex/walkpaint-v069-20261024。沿用 Android 原应用、原签名、SQLite schema3 和真实 GPS 分段。

## 采用的改进

- 日历原本可以换月，但顶部汇总固定本月，列表固定最近五天；本版将月份选择提升为同一状态，联动日历、月/年统计与完整当月列表。月份弹窗直接列出有记录的月份；查找和收藏继续跨全部日期。
- 从竞品的轨迹可迁移能力中选择当天 GPX 导出。采用 [Topografix GPX 1.1](https://www.topografix.com/gpx/1/1/) 的 WGS84 坐标、UTC 时间和 trk/trkseg；参考 [世界迷雾的 GPX/KML 互通](https://fogofworld.app/zh-hans/)。不复制外部代码或素材。输出保留原点，不用地图 GCJ-02 或显示简化结果，不插值补断档；演示轨迹、备注、标签不输出。GPX 不能代替包含全部资料的 JSON 备份。
- 本轮不加入照片权限、AI 服务、复杂挑战或新数据库迁移。优先让现有足迹能完整回看、由用户带走。

## 正式包路径

审阅复用 PR124 固定提交 69c01a3a2fb9ed83d6c00a4fe6de4b39bd33970d 的构建/签名隔离实现，保留 main 的文档更新；没有自动合并其他业务 PR。android-build 仅注入已有地图配置，android-signing 独立签名；私钥不离开受保护作业。补齐 V1/V2/V3 显式签名及发布脚本 Linux 工具名。0.69 / code77。

## 验证状态

验证源码提交：`a3be66674e832e2330920ef9cf972683fb0c6588`。10项针对性检查 passed；完整 `bash cloud/check.sh` passed：541项 JVM/Native Graphics/Compose（0失败/错误/跳过）、开发APK assembleDebug、lint（0 errors / 60 warnings / 2 hints）、Python18+23。签名策略7项、actionlint 1.7.7、git diff --check passed；结果与截图摘要见 checks.json。首次检查中的测试选择器/数值格式问题已修正并重跑，未沿用失败结果。历史报告不作为本版结果。原生 UI 使用 Robolectric Native Graphics / Compose、独立合成轨迹与内容 URI，不是实体手机或真实高德联网验收。最终正式产物的包名、版本、架构、签名与下载摘要另行核对。

实体手机覆盖安装、真实高德底图、户外 GPS、锁屏耗电：not run。本版不宣称这些已通过。


## 已发布并完成下载核验

[正式版 v0.69](https://github.com/h516df/walkpaint/releases/tag/v0.69)，源码与标签绑定 `454148a333e9e269c84e46c29cf3b5e0880fd883`；与 a3be666 的应用/构建/工具内容一致。PR126 与 main 检查 passed。原签名 [40085397252](https://github.com/h516df/walkpaint/actions/runs/40085397252) passed；GitHub 作业 [40086151447](https://github.com/h516df/walkpaint/actions/runs/40086151447) 使用同一产物恢复草稿并完成正式发布，未提取私钥或移动标签。

三个公开附件均无登录实际下载，逐个核对大小和 SHA256；下载后的 APK 再次验证 cn.walkpaint、0.69/code77、非调试、ARM64 与原证书 V1/V2/V3。54,716,819 bytes / SHA256 `be501faab3a6a00006518c3b7693ce6a399be2c877842afa004696f89cf2568c`。Release 非草稿、非预发行，latest 指向 v0.69；说明 JSON 解码和校验清单匹配，见 publication.json。

连接器 CLI 的构建附件下载受拒，沿官方 GitHub 连接器取回；当前连接的 uploads 端401，沿最小 GitHub 作业令牌发布，保持同一草稿、标签和APK。上述通道问题已解决，没有把元数据成功当附件下载成功。实体手机安装和户外/锁屏验收仍为 not run。
