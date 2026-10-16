# 构建与签名

JDK21、Android SDK36，使用Gradle Wrapper。SDK路径可由ANDROID_HOME指定，local.properties始终被Git忽略。

```sh
./gradlew :app:assembleDebug -PwalkpaintDeveloperBuild=true
./gradlew :app:testDebugUnitTest -PwalkpaintDeveloperBuild=true
```

Windows使用gradlew.bat。开发模式使用本机开发签名、独立包名cn.walkpaint.dev，无需正式私钥，与正式App并存；开发版从源码更新。开发模式的release未配置正式签名，不能用来正式发布。

未配置高德Key时不初始化高德，备用地图的实际支持另验；需要高德时为自己的包名和签名申请合法Key，参见AMap_SETUP.md。

正式包cn.walkpaint保持原签名。私钥与备份放在公开Git之外，默认构建在原签名文件缺失时拒绝继续；发布工具核对实际原证书身份。

```sh
./gradlew :app:assembleRelease -PwalkpaintArm64Only=true
```

公开发布沿scripts/publish_release.py，显式--public-release并绑定已推送完整SHA。核对非调试属性、版本、ABI、原签名V1/V2/V3、说明和摘要后，先上传草稿并检查资产再发布。不得覆盖既有版本。

发布输入固定已验收APK，发布后重新下载核对实际字节。升级不卸载清库，必须同签名、versionCode递增；回退也须兼容schema3，不能直接装旧包。

自动化 Intent 诊断导出仅在 `walkpaintDeveloperBuild=true` 的 debug 包中可用，官方包不响应；旧诊断文件会在启动时清理。正式包的原生验收使用同签名 androidTest 内的 FieldReportProbe，不在生产包重新开放测试入口。
