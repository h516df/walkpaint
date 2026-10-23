# v0.64 验收：单引擎地图、必过首启、历史回放

分支 `v0.64-single-map-onboarding`，基线 `bfb27a1`（v0.63 / code71）；PR #105 已合并到 `main`（`6672788`），发布 tag
`v0.64`（资产 `WalkPaint-v0.64-arm64.apk` 54,571,289 字节 / SHA-256 `ad586a69…`，公开下载已核对 HTTP 200 且长度一致）。本版三件事：

1. **地图只留高德一套引擎**：删除 MapLibre/OpenFreeMap 备用瓦片引擎（`app/build.gradle.kts` 依赖、`MainActivity.kt` 全部引用、`RoadColorProbe.kt` 探针改为纯高德、NOTICE 许可行、文档口径）。APK 由 63.5 MB 降到 52 MB，包内不再有 `libmaplibre.so`，原生库只剩 `libAMapSDK_MAP_v11_3_100.so`、`libandroidx.graphics.path.so`、`libapssdk.so`。
2. **首启不再能把地图留成黑屏**：地图授权改成不可跳过、不可点走的整屏 `DesignMapConsent`（新增 `MobileDesign.kt`）；未启用时地图区显示「地图还没打开」+「开启高德地图」，文案用主题令牌，浅色与深色都读得清；地图右下角缩放控件原来硬编码白底，深色下是白块，已改用 `Canvas`/`Line`/`Ink` 令牌。
3. **历史回放**：`Replay.kt` 的 `replaySlices(trips, progress)` 纯函数，选某一天后路线从起点沿当天轨迹画到终点，可播放、暂停、继续、重播、关闭。

## 本轮实测证据

命令都在 `<repo>`，设备为专用 `emulator-5560`（`WalkPaint_Google_API36_V044`，x86_64 + ARM64 native bridge），安装的是**正式签名 ARM64 包**（code72/versionName 0.64）。

| 检查 | 命令/探针 | 结果 |
| --- | --- | --- |
| JVM 单测 | `gradlew :app:testDebugUnitTest -PwalkpaintDeveloperBuild=true` | 72 套件 / 435 项 / 0 失败 0 错误（基线 71/431，新增回放切片 4 项） |
| Python | `python -m unittest discover -s tests`；`discover -s scripts -p 'test_*.py'` | 18 OK；23 OK |
| 正式构建 | `gradlew :app:assembleRelease -PwalkpaintArm64Only=true` | BUILD SUCCESSFUL，`app-release.apk` 54,571,289 字节（发布资产） |
| 打包可复现性 | 同一文件树在分支上与合并后的 `main` 各构建一次 | **不可复现**：分支产物 `d939aa4f…` / 54,571,294 字节，`main` 产物 `ad586a69…` / 54,571,289 字节；两棵树内容相同，差异来自打包字节（时间戳/压缩），故发布前用 `main` 产物**重新装机重跑一遍探针**，证据绑定到发布资产的 SHA |
| 签名 | `apksigner verify --verbose --print-certs`（v2/v3）与 `--min-sdk-version 23 --max-sdk-version 23`（v1） | v1/v2/v3 全部 true；Signer SHA-256 `21b74e3e…c967`，与历史发布同一身份 |
| 单引擎运行 | `scripts/android_replay_acceptance.py` 前的启动截图 | 高德视图生效，覆盖层与迷雾照常绘制，无 `libmaplibre.so` 加载记录 |
| 回放 | `python scripts/android_replay_acceptance.py` | `replay_bar_shown=true`，路线像素 `[91,148,268,328,420,420,836,836,836]` 单调增长到 9 倍；`route_grows_while_playing`、`replay_reaches_full_route`、`pause_freezes_replay`、`replay_restarts_after_finishing`、`close_restores_day_card` 全 true（发布前在发布资产 `ad586a69…` 上重跑，结果一致） |
| 回放不写库 | 同上，`replay_changes_no_database_rows=true` | 前后都是 `[10,4893,0,0,24]` |
| 撤回授权不留黑屏 | `work/walkpaint-v064/verify_revoke.py`（我的 → 更多设置 → 隐私与权限 → 地图服务与隐私 → 撤回地图授权） | `settings_row_reachable`、`privacy_dialog_shown`、`dismiss_label_is_revoke`、`after_revoke_readable`（出现「地图还没打开」）、`after_revoke_recoverable`（出现「开启高德地图」）、`placeholder_opens_consent`、`recovered_placeholder_gone` 全 true；五表行数不变 |
| 覆盖安装保数据 | `adb install -r` 覆盖 0.63 | trips/points/roads/coverage/road_matches = 10/4893/0/0/24 保持 |

原始 JSON：`verification/v064-replay/acceptance.json`、`verification/v064-single-engine/revoke.json`（`verification/` 被 Git 忽略）；截图在会话工作区 `work/walkpaint-v064/shots-strip/`。

## 未验收与限制（如实保留）

- **高德底图瓦片在 x86_64 AVD 上表现不一致，原因未查明**：`libAMapSDK_MAP_v11_3_100.so` 经 native bridge 实际加载，搜索/地理编码可用（注入北京坐标能解析到「东华门街道附近」）。实测三种视图：① 相机落在自己的巴黎足迹上时**底图有真实出图**（Rue de Rivoli、巴黎市政厅、5Hôtel de Ville 等地名与道路配色，见 `shots-strip/21-published-beijing.png`）；② AVD 默认位置山景城为空；③ 注入北京坐标并点定位后整片为黑，只有蓝点（`shots-strip/22-locate-beijing.png`、`23-restart-paris.png`）。换 `-gpu swiftshader_indirect` 后同样如此，logcat 没有鉴权或 GL 报错。这**不是本版引入的回归**：删 MapLibre 之前（v0.63 时代）在同一 AVD 上也是迷雾圆盘正常、底图不出图。**底图通用观感只能以真机为准**，本轮不作已通过声明。
- **实体手机未连接**：安装、户外、锁屏、长时间后台与拖动流畅度未验收。
- 删引擎前依赖备用引擎的 6 个验收脚本（`android_colors_acceptance`、`android_colors_remaining`、`android_fog_line_acceptance`、`mobile_ui_acceptance`、`android_follow_offline`、`android_direct_trail_acceptance`）已改成高德口径，但**只做了字节编译与两个离线测试套件**，其中「撤回授权」路径由 `verify_revoke.py` 在本轮真机化验收；`android_follow_offline.py` 的蓝点颜色常数 `(48,139,211)` 是照同类脚本推断的，**未单独实测**；`android_direct_trail_acceptance.py` 仍被本轮范围外的旧选择器（`涂色颜色`）挡住，本轮没有让它跑通。
- `docs/evidence/` 与历史 release notes 里的备用引擎记录是历史证据，不回改。

## 兼容与回退

- schema 仍为 3，本版不改数据库；沿用原签名，`versionCode` 71 → 72 递增；覆盖安装保留足迹与设置。
- 回退须同签名、schema 兼容、`versionCode` 不降低；不得卸载清库。
