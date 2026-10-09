# 高德 Android 地图：v0.8 模拟器可运行，真机待验收

当前使用既有官方 Android SDK 11.3.100。Issue #4 已确认本机忽略配置中的受限 Android Key，v0.8 同签名 APK 真实加载 SDK 底图；值未读取/打印/提交。地图是首页主角，原 GPS 调试路线移除，仅确认的 OSM 区间上色。用户 vivo、中国真实道路对齐仍未验证。以下配置步骤供未来重建参考；当前不用重复申请或粘贴 Key。

## 必要用户授权（一次）

1. 登录高德开放平台 https://lbs.amap.com/ ，按官方要求申请 Android 地图服务/Key，查看 https://developer.amap.com/api/android-sdk/guide/create-project/get-key 。
2. 包名必须为 **cn.walkpaint**，当前调试版签名 **SHA1 = FA:E3:DF:35:B3:2F:A7:DF:41:B3:F8:53:79:3E:A6:9D:39:5B:8E:52**。这是证书摘要，不是私钥。正式版若换签名须按平台重新登记且妥善处理安卓升级兼容。
3. **不要把 Key 发到公开聊天/Issue/仓库**。在项目已有、被 Git 忽略的 local.properties 追加一行纯字母数字格式：walkpaintAmapKey=自己的AndroidKey。构建系统从本地文件读取，不提交 Git；Key 仍会存在安装包中，必须由开发者平台的包名+签名限制保护。
4. 构建同签名 code>=9 的 APK，直接覆盖安装，首次使用高德地图时会另弹出地图 SDK 隐私说明，同意后才初始化 SDK。若不同意，可继续使用 OpenFreeMap 记录本地足迹。APP 会打开高德隐私政策 https://lbs.amap.com/home/privacy/ 供阅读。
5. Android 真机校验：首页是否显示高德道路/地点中文底图、官方署名、小蓝点/已确认道路是否对齐、切换页面与恢复、弱网和隐私拒绝回退；确认官方 Key 实际生效前不能说已完成高德上线。

## 已实施和边界

- AndroidManifest 声明官方 Key meta-data，BuildConfig 在运行时判断是否有 Key；没有合法 Key 不触发高德地图初始化。
- 高德初始化前先经单独的 SDK 告知与用户主动同意，再执行 MapsInitializer.updatePrivacyShow 和 updatePrivacyAgree。
- 原始 SQLite/JSON **一直使用 WGS84**；AMapMapController 通过 CoordinateConverter.CoordType.GPS 在显示路径转换为高德使用的坐标，绝不回写数据库或双重转换。
- 同一应用的 GPS foreground service 与高德底图不耦合，拒绝高德不会停止足迹记录。
- SDK/地图在线服务会处理必要的位置和设备/网络信息。高德提供方：北京高德图强科技有限公司；应按官方最新版隐私政策、应用合规要求及非商业配额处理。
- 国内地图备选顺序：**高德 → 腾讯 → 百度**。目前腾讯/百度 SDK 均未集成，后续只有高德不可行或确实不满足需求时再实施，不叠多套无用依赖。

官方参考：
- 高德 SDK 更新日志：https://developer.amap.com/api/android-sdk/changelog/3d
- 高德隐私合规要求：https://developer.amap.com/api/compliance-center/check-and-reference/sdkhgsy
- 官方隐私政策：https://lbs.amap.com/home/privacy/
- 高德 Android SDK Gradle：https://developer.amap.com/api/android-sdk/guide/create-project/android-studio-create-project

注意：最新 11.x 官方 SDK SO 以 ARM 架构为主，基于 x86/x86_64 的模拟器可以继续使用 MapLibre fallback，但不代表高德 ARM 真机 SDK 已经验证；实际部署以真机测试为准。
