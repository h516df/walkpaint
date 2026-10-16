# 高德地图配置

项目引用官方Android SDK，源码不携带维护者的Key。未配置时不初始化高德，使用既有备用地图路径。

1. 在[高德开放平台](https://lbs.amap.com/)按官方要求申请Android服务并核对使用条款。
2. 注册自己的包名与签名SHA1，独立开发包为cn.walkpaint.dev。证书摘要可公开，私钥不能公开。
3. 在被Git忽略的local.properties设置 `walkpaintAmapKey=replaceWithYourAndroidKey`，或使用对应Gradle属性，不把实际值放入Issue/PR。
4. 完成独立地图隐私告知与同意后才初始化SDK；拒绝高德不等于同意其他联网服务，也不应停止独立GPS记录。
5. 地图Key会包含在Android客户端，须用平台包名与签名限制保护，不能当服务器秘密；不得在App中放服务端Key或client secret。

原始足迹保持WGS84，显示转换不回写数据库。高德、备用地图、模拟器native bridge和实体手机分别验收，不能凭源码默认值证明实际引擎。

官方说明：[获取Key](https://developer.amap.com/api/android-sdk/guide/create-project/get-key)、[SDK合规](https://developer.amap.com/api/compliance-center/check-and-reference/sdkhgsy)、[隐私政策](https://lbs.amap.com/home/privacy/)、[服务协议](https://lbs.amap.com/home/terms/)。
