# Issue #2 模拟器证据

三个截图来自明确测试的 MuMu/API35 和固定公开 Oxford OSM 道路上的模拟 GPS，并非用户真机或私人实走轨迹。道路 © OpenStreetMap contributors / ODbL-1.0；原始公开fixture见 app/src/test/resources/oxford-roads.geojson。

- issue-2-emulator-map.png：后台/关屏70秒保存之后，累计青绿道路与蓝点实际渲染，新增匹配4段。
- issue-2-emulator-interruption.png：停止回调65秒且服务存活，界面明确“GPS回调已中断”。
- issue-2-emulator-day.png：选择日期后的已确认道路，只读显示，原断点保留。

运行APK SHA256：8c9f47d6f9a08d4a9f1e3b5b74b8b9f89be595eb8baa7c63f4bafc9002d5b9dc。业务提交ec0d2b5；完整整链在强停重开时FAIL并观测系统进程重启，原因未确定；恢复脚本ed5f86e对同APK单独复验重开/暂停/再入库PASS。真机BLOCKED，完整准确证据边界见HANDOFF。截图不能代替真机或整链稳定性验收。
