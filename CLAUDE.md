# 项目规则

通用规则以 `D:\Projects\agent-rules\AGENTS.md` 为唯一来源。本项目真实状态和证据以最新 `HANDOFF.md`、Git 和 APK 指纹为准。

- 包名 cn.walkpaint、原签名和原SQLite trips/points必须保留；只覆盖升级，不卸载/清数据/降到schema1。
- 默认单写手；当前任务分支feat/v06-nearby-roads，未合并/推送/发布。
- 道路下载只在本次明确同意区域/IP后请求Overpass；不要自动发送私人GPS或添加备用主机。
- 真实Android验收只允许显式测试模拟器；当前127.0.0.1:16384是MuMu，不是真实vivo手机。
- 不在MuMu运行svc wifi/data disable：它的ADB依赖虚拟网络，会切断恢复通道。网络失败用已有RoadDownloadTest；若必须真机网络测试，先明确可恢复的控制入口。
- 测试必须保存原始行比较、最终APK指纹和清理结果；部分通过与未测项分开报告。
- 当前网络/测试清理和最终手机包的模拟器入口复验已通过；真机未连接且未发布，准确指纹和下一步见HANDOFF。
