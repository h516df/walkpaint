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
