# 高德地图配置

官方安装包默认使用高德地图，地图配置随安装包提供。普通用户打开 App，点击“同意并开始”，按系统提示允许精确定位即可开始探索，不需要注册高德账号、填写 Key 或下载道路包。后台增强设置可以之后在“我的 → 后台记录设置”中按需开启。

以下步骤仅供从源码开发。项目引用官方 Android SDK，公开源码不携带维护者的 Key。独立开发包未配置时不初始化高德，地图区显示配置提示，没有备用地图引擎；正式包缺少地图配置时拒绝打包。

1. 在[高德开放平台](https://lbs.amap.com/)按官方要求申请Android服务并核对使用条款。
2. 注册自己的包名与签名SHA1，独立开发包为cn.walkpaint.dev。证书摘要可公开，私钥不能公开。
3. 在被Git忽略的local.properties设置 `walkpaintAmapKey=replaceWithYourAndroidKey`，或使用对应Gradle属性，不把实际值放入Issue/PR。
4. 完成独立地图隐私告知与同意后才初始化SDK；拒绝高德不等于同意其他联网服务，也不应停止独立GPS记录。
5. 地图Key会包含在Android客户端，须用平台包名与签名限制保护，不能当服务器秘密；不得在App中放服务端Key或client secret。

原始足迹保持WGS84，显示转换不回写数据库。高德底图只有模拟器native bridge和实体手机两条真实验收路径，不能凭源码默认值证明实际引擎；底图瓦片渲染必须以真机为准。

官方说明：[获取Key](https://developer.amap.com/api/android-sdk/guide/create-project/get-key)、[SDK合规](https://developer.amap.com/api/compliance-center/check-and-reference/sdkhgsy)、[隐私政策](https://lbs.amap.com/home/privacy/)、[服务协议](https://lbs.amap.com/home/terms/)。
