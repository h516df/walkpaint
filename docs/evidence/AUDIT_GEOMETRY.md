# 2026-10-13 轨迹裁剪对抗验收（Issue #50）

生产基线 v0.46 / `87b75d52283d7dbdc99af91e4d073a9e49206047`。编译后的真实生产 `TrailShape.network` 以匿名 U 型六点轨迹复现：先离开视口，再从另一侧进入，会将两条可见边连成未走过的捷径。另一个回归是两端在视口外的真实穿越线段消失。

改为逐原始线段裁剪，离开视口即结束可见片段；交点仅用于显示，不改保存坐标、时间或数据库。既有 5% 边缘余量与显示简化继续保留。三个行为回归先有两个失败，修复后全部通过；完整 JVM 测试 334 项通过。

候选仍为未发布的 0.46/code48，APK SHA256 `97c16ab30590947ecfbefbf369ae2853d0a0bf07c9e74c0d56faa923a2790751`。在独占 `emulator-5560 / WalkPaint_Google_API36_V044`、Android16、ARM64 APK、高德实际 JNI 路径上，MAP_SCENE、HISTORY_CAMERA、AMAP_RAPID_ZOOM、AMAP_ALIGNMENT、DATA_PRESERVATION 均通过。原有五表摘要与正式 v0.46 证据一致（7 行程/4865 点）。证据见 [normalized result](audit-geometry/result.json)、[runtime](audit-geometry/runtime.json)、[真实缩放画面](audit-geometry/geometry-zoom.png)。匿名密集画面为既有内存 fixture，不写入用户数据库。

目标 vivo 真机未连接；不能据模拟器结果宣称真机 GPS、帧率、更新授权或安装已通过。此次不修改 GPS、备份、地图引擎与数据格式；回退程序时原有数据保留。
