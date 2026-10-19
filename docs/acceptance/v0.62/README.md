# 0.62 地区行记与后台隐藏验收

正式候选为0.62/code70；APK、源码指纹与环境见 [evidence.json](evidence.json)。本轮保持名称图标、GPS过滤、50米揭雾/25米网格、SQLite schema3和原签名。431 JVM、41 Python、ARM64正式包构建通过。

## 已验证

- 最终APK实际安装后，高德反查与行政区查询返回东城区110101、北京城区、北京市，真实县界593个顶点，见 [联网结果](region-network-final.log)。同步DistrictSearch正常返回的异常字段为null，原来被误判失败；本轮同时修复地区索引及县界路径，单测覆盖null/1000、空结果与错误码。请求由本任务模拟器进程代理及DNS接通，未更改用户VPN；此前直连1802为网络超时。
- 最终APK后台隐藏后回桌面，真实记录服务连续提交9个模拟GPS点；桌面重开、隐藏开关持久化、通知偏好未变均通过，见 [后台结果](recents-recording-final.log)。这证明专用AVD该路径，不能承诺手机系统永不停止后台。
- 原AVD0.52/code58覆盖到首个0.62候选时，五表计数与摘要完全相同；最终APK再次覆盖安装前后五表也完全相同。中间两次后台探针各有意新增9个合成点，因此不把跨记录前后的计数称为相同。见 [原升级前](data-before.log)、[原升级后](data-after-upgrade.log)、[最终安装前](data-final-after-recents.log)、[最终安装后](data-final-after-reinstall.log)。
- 地区UI使用明确合成的31天足迹与县界夹具，验证县/市/省切换、实际天数、两项面积奖获得、100天奖未解锁、重开获得时间不变。1.3倍字体实测向上展开面板后，微光/星火/流光与日期条件均可达可读，见 [大字展开面板](administrative-detail-expanded-large.png)。**该次探针业务断言和截图通过，末尾清理导航失败**：展开面板的Back先收拢，探针误以为直接关闭后寻找“返回”。已移除不必要的清理导航，未再次跑整条探针；不能将该日志写成无失败全通过。见 [原始日志](administrative-large-final.log)。
- 成长页周目标重开保持、纪念章时间去重、我的/足迹导航与后台开关，1.0/1.3字体通过；附近2公里夹具为13.7%。这部分证据来自本轮较早0.62候选，之后只修同步地区查询，相关UI/GPS/覆盖算法未变，未重复全跑。
- 最终APK的高德ARM运行证据已绑定APK摘要；25帧缩放迷雾采样未知区最低黑度1.0，排除区域仍为实际控件bounds，见 [运行](amap-runtime-final.log)、[迷雾](map-scene-final.log)。

## 界面

默认：[区县](administrative-counties.png)、[城市](administrative-cities.png)、[省份](administrative-provinces.png)、[详情展开](administrative-detail-expanded.png)、[行记](growth-overview.png)、[我的](journey-profile.png)、[足迹](journey-trips.png)。

1.3倍字体：[区县](administrative-counties-large.png)、[城市](administrative-cities-large.png)、[省份](administrative-provinces-large.png)、[全部纪念章](administrative-detail-expanded-large.png)、[行记](growth-overview-large.png)、[我的](journey-profile-large.png)、[足迹](journey-trips-large.png)。截图均为合成内容，不包含用户轨迹。

## 限制与恢复

真实县界已通过，但地图完整道路瓦片仍未获得可确认的渲染证据；map_loaded或县界成功不等于街道路网通过。实体手机、户外、长时间锁屏后台、深色主题本轮未验收；Issue17/96保留。地区自动整理需要联网与既有定位服务同意，未知边界显示暂未计算，不冒充附近2公里或授地区面积奖。

派生地区索引可安全重建，失败保留原轨迹；每次打开最多40次网络请求、连续3次失败暂停、退出取消，实际缓存县界与省市层级减少重复调用。回退需原签名、兼容schema3及不降低versionCode，不卸载清库。字体恢复1.0、模拟GPS权限恢复，任务AVD结束；正式发布与资产回读由root执行。
