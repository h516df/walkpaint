# 足迹涂色 WalkPaint (Android v0.1)

用彩色线条积累自己真实走过的足迹。项目位于 `D:\Projects\WalkPaint`，与其它 Android 项目独立。

## 已实现

- MapLibre 原生地图 + OpenFreeMap 的 Liberty 底图，支持缩放/平移。
- 用户手动启动、结束 GPS 记录；在 Activity 可见时启动位置类型的 Android 前台服务，记录期间有持续通知和通知栏「结束记录」操作。锁屏后可继续记录，但手机厂商省电策略仍可能终止服务。
- 每个有效 GPS 点及时写入应用私有空间 `tracks.json`；重开 App 后累计路线、总公里数和记录次数仍可查看。
- 过滤定位精度差、明显跳点和静止抖动；蓝绿色为真实足迹，紫色为演示轨迹，演示数据不计入统计并可单独清除。
- 不请求 `ACCESS_BACKGROUND_LOCATION`，不会私下全天候追踪。位置轨迹不上传到任何业务服务器；在线地图加载会向地图图块服务请求所查看地区的数据。
- 地图数据署名：© OpenStreetMap contributors / OpenFreeMap。

## 使用

1. 安装 `足迹涂色-v0.1-debug.apk`（Android 8.0 或更高版本；Android 16 尚未真机测试）。
2. 首次打开需要联网加载地图；可点「查看演示」看紫色涂色效果，清除演示不会影响真实路线。
3. 点「开始记录」，允许**精确位置**与通知权限，打开系统定位。等到 GPS 可用后步行。
4. 手机锁屏期间记录依赖前台定位服务；走完点击 App 的「结束并保存」或通知上的「结束记录」。在 vivo/其他严格省电手机上，可能还需要允许 App 后台运行或关闭该 App 的严格电池限制。
5. 点「定位我」跳转到当前可用的最近一次 GPS 定位结果。

**数据风险：** v0.1 只有本机持久化，没有 GPX/JSON 导出与恢复。卸载 App、清除数据或手机故障会让足迹丢失；目前属于可试用原型，不建议作为唯一长期备份。下一版优先做导入导出后再用于正式长期记录。

**产品边界：** 当前是 GPS 折线叠加，不是道路精确吸附，也不计算真实的道路覆盖率。快速运动和 GPS 精度不佳的点会过滤，可能导致少量缺口。地图需要网络；离线时已有轨迹存本机，但底图可能无法加载。

## 构建与测试

本机已准备 JDK 21、Android SDK 36、Gradle 9.1.0，并复用 `D:\Apps\Android\gradle-home` 已下载的 Gradle 发行包，不复制任何旧项目密钥或签名。

在项目根目录运行（示例）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:GRADLE_USER_HOME = 'D:\Projects\WalkPaint\.gradle-user-home'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug -Pkotlin.compiler.execution.strategy=in-process --console=plain
```

输出 `app\build\outputs\apk\debug\app-debug.apk`，项目根目录的中文名称 APK 为供手机安装使用的相同调试版文件。调试版使用 Android SDK 自动生成的本机 debug 签名，未上传到应用商店。请勿直接把这份签名当成正式发布签名。

测试用例在 `app\src\test\java\cn\walkpaint\TracksTest.kt`；测试覆盖 GPS 距离、异常点、断段、JSON 序列化、磁盘读写以及损坏文件不覆盖。

## 许可与隐私提示

地图界面用开源 MapLibre Android 与第三方 OpenFreeMap 提供的公共图块服务，使用时须保留地图署名。当前不含账号、服务器、广告、追踪 SDK。地图瓦片服务能观察到请求区域和网络信息，不能将“足迹不上传”理解为完全无外部网络请求。
