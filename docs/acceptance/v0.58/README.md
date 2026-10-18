# 高德开箱即用与50米圆形揭雾

409 JVM、23 Python通过；正式ARM64包在API36 x86_64 native bridge实际加载高德，AMAP_RUNTIME_PASS；圆心与8方向35米内显露、75米外保持黑雾的像素验证通过；AMAP_ALIGNMENT_PASS（1.414px中心误差，覆盖层变换误差0）；9000合成点25次缩放采样通过。map_loaded和雾像素不等于所有底图瓦片与实体手机已验收。

安装包 SHA256：`5ca20c5ec7bef2d360a2955e59d0f5656c546f172cf906ca9a30934a5c770e34`。大小 66430968 字节。源码及安装包对应本次分支；GitHub合并后标签绑定完整提交。

用户手机未连接，真实手机网络、本人系统覆盖安装未验收。保留数据和签名；出现问题采用后续递增版本修复，不建议降级写入。

## 参考与许可

高德应用Key由开发者绑定签名/包名：https://lbs.amap.com/api/android-location-sdk/guide/create-project/get-key/ 。普通用户仅同意隐私服务，无需注册开发者。圆形揭雾参考 https://github.com/denlogv/organicmaps-fog-of-war ，使用原有绘制器，无引入代码。
