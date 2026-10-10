# v0.16：在联网高德地图上直接涂色

用户纠正： “在足迹涂色的高德地图上，直接涂色的，不需要准备地图”；“这app联网的呀”。当前契约优先于旧版要求道路包、只显示道路匹配或禁止GPS折线的历史规则。

## 范围与行为

- ARM 手机仍使用原高德官方联网 SDK；已有独立同意不重复询问，新安装保留一次高德服务隐私同意。没有道路准备、导入或附近下载步骤。
- 已保存的真实GPS轨迹直接交给高德 Polyline 绘制，首次单点可见；高德只在显示边界转换一次WGS84。Google x86_64 使用既有 MapLibre 回退，同一条 RecordedTrail 几何。
- 首页/更多设置不再出现准备地图入口；当天/历史、颜色、备份、更新入口保持。足迹页用累计记录里程，重复经过会累计，不能称去重道路里程。
- 保存支持准确步行、骑行和正常驾车，速度安全上限60m/s；快于8m/s需精度不差于20m，有可靠GNSS速度时需与位移相符。保留静止、精度、非递增时间和异常跳点过滤。
- 超过45秒的定位空档可保存新的开始，但不补画中间未记录路线；不同trip/segment、重复时间、异常速度和演示数据不串线。
- cn.walkpaint / schema2 / 原签名不变，code18。同签名覆盖升级，禁止卸载清库。旧道路和匹配表继续保留，仅作兼容诊断，不控制显示；原GPS仍在本机，没有新增上传或后台服务。以前未保存的点无法恢复。

## 自动与实际入口证据

- 186 JVM测试全部通过，包含步行1.5m/s、骑行7m/s、驾车15/33m/s、无速度的精准驾车、漂移/精度/瞬移、断档恢复、跨trip/segment及直接反馈无道路准备归因。
- 7 Google设备身份守卫测试通过；全部设备操作限 emulator-5556 / WalkPaint_Google_API36 / boot_completed=1。
- 实际Android隔离SQLite：无road catalog，7个公开巴黎测试点全部通过addPosition；5+2两段，总计约500米，中断期间不连接，road matched=0。实际安装App增加同样公开夹具；重跑识别原夹具不重复添加。
- 原v0.15的3 trips / 19 points / 6 roads / 16 road_matches / 1 coverage升级时完全相等；加入夹具后的4/26/6/22/1作为完整UI最终复跑基线，五表原行全部保留，PRAGMA integrity_check通过。
- Google真实UI：首页直接轨迹13259像素、当天6808、换雾蓝6810、联网备用底图6808、重开13259；入口没有准备地图要求。已打开联网底图并检查截图，不能把这张MapLibre截图当ARM高德截图。
- 320dp足迹统计、每日地图导航及稳定渲染截图通过；测试后所有原偏好与wm视口实读恢复，App停止，无权限/模拟provider改动。固定公开夹具点保留，旧数据未删除。
- 通用包与arm64手机包全部dex及release-notes一致；手机70,271,924字节；两包SHA、原证书、生产源逐文件指纹在v016/acceptance.json。安装App指纹对应通用包。

## 失败与修正边界

首轮失败测试先因新类型/参数不存在而编译失败。主机首轮图像断言缺Pillow，改用已有依赖运行时；重跑夹具旧时间回退被数据库拒绝，改为幂等，不改产品逻辑/删除原行。联网冷重开3秒截图尚在加载，等12秒后同APK通过。小屏首次截图早于导航画面稳定，单独等DOM及画面稳定复拍，无产品源码变化。不伪称第一次整轮都通过。

## 剩余验收与回退

新ARM高德渲染未实测，现有vivo手机的实际驾车、长期后台/锁屏/厂商耗电策略未验收；#4/#6保留开放。SDK接线与编译/共享几何测试不替代手机像素证据。可从原v0.15兼容源码构建同签名schema2、code>=18修复包回退，不降级APK或清库。

本地候选待本次v0.16私人发布确认；v0.15发布已完成，旧授权不能推定新版本已发布。不合并历史PR。业务基线为f202d8a；验收JSON保存本次生产源码指纹，可对应后续提交复查。仓库没有配置Actions，不称远端CI通过。

官方API核对：[高德折线绘制](https://lbs.amap.com/api/maps-sdk-for-android/guide/draw-on-map/draw-polyline)、[PolylineOptions](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/3D/com/amap/api/maps/model/PolylineOptions.html)、[Android Location速度与精度](https://developer.android.com/reference/android/location/Location)。
