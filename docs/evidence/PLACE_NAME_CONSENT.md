# 地名查询同意与坐标边界（Issue #40）

用户要求继续把可自动完成的工作搞好。DSH PR #41 的补充复核实际记录了高德搜索 SDK 缺少隐私初始化的警告；原代码也把 SQLite/GPS 的 WGS84 坐标标成 AMAP 输入。已发布 v0.43 的地图/迷雾修复保留，不改原签名、数据库 schema 或自动记录实现。

本分支从 PR #38 的合并提交 `4b99e60ef44186b7b248e172f59007317d7dd6e0` 开始，只处理地名请求边界：

- `PlaceNameRequest` 在调用服务前核对实时同意与坐标有效性，拒绝或撤回时不调用初始化/请求闭包；响应到达时再次核对同意，异常退回普通标签。
- `PlaceName.resolve` 复用现有 `ui_onboarding/amap_sdk_consent` 和已有 Key，只有合法同意之后才依次调用 ServiceSettings 隐私接口，再构造 GeocodeSearch；输入采用官方 `GeocodeSearch.GPS`，不改数据库坐标。
- 同意后清除位置去重缓存，当前位置可立即重查；撤回时清空地名并同步撤回服务同意。主界面用请求序号与当前位置拒绝旧回复，包括撤回后在同一位置重新同意的情形。
- 同意说明准确写明地图和地名查询用途，不自动替用户同意。

官方接口来源：[初始化顺序](https://developer.amap.com/api/android-sdk/guide/create-project/dev-attention)、[GPS 查询坐标类型](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/Search/com/amap/api/services/geocoder/RegeocodeQuery.html)。

## 已验证

原基线 318 项 JVM + 5 项请求边界回归，合计 323 项通过（拒绝、非法坐标、原 GPS 不变、撤回后的待回复、失败后恢复）。现有 27 项 Python 回归通过。仅构建 AndroidTest 包成功；具体业务源码、探针与测试包指纹见 [inputs.json](place-name-consent/inputs.json)。最终本分支没有独立递增版本或替换更新说明，便于主写手合入当前版本。

## 实机路径尚未跑过

新增 `PlaceNameProbe` 仅以北京/上海公开点检验：隔离偏好文件里的拒绝/撤回是否零次初始化、官方真实查询是否成功并落在对应城市，原 App 偏好保持不变。新增 `AmapAlignmentProbe` 直接读取 SDK 原始相机、屏幕投影、visibleRegion 和 native GroundOverlay bounds，验证中国坐标只转换一次，结束恢复原相机；不新增生产诊断 API。

两探针已经编译，但尚未在当前合并包上运行。原计划的本轮 AVD 操作在安装身份核对时停止：设备已被另一个当前 WalkPaint 写手占用，安装的是其 v0.44/code46 候选 `55c2fcbd868953872210a3867e76b4bea1a26b287da3d42318e454646feb8870`，与本分支预览不同。没有覆盖主 App、清库、卸载、停止该设备或发布另一份 v0.44。曾更新测试 APK，后续设备负责人应安装与最终源码对应的测试包，再验 `-e placeNameProbe true` 和 `-e amapAlignmentProbe true`。

目前还有另一位 Codex 正在处理用户最新首页/GPS需求，目录 `D:\Worktrees\WalkPaint\codex-home-fog-gps-v044`、分支 `agent/codex/home-fog-gps-v044`；该工作区只读。主写手整合时须保留最新界面和 GPS 修复，重建并复验对应的同一包，不分发本分支旧界面预览。本人手机仍未通过 ADB 或已发现的无线调试连接；本轮不代本人授权/确认安装。

回退只需还原本轮地名/测试提交；原 Release 不覆盖，用户数据库不清除。源码可审查与真实运行通过分开报告，Issue #40 在对应真高德查询验证通过前保持开放。
