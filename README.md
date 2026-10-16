# 足迹涂色 · WalkPaint

Android 本地足迹应用：记录步行、骑行与驾车，沿可信的已保存轨迹揭开地图迷雾。探索、足迹、我的位于底部，支持按日期回看、本机皮肤、JSON 备份和合并恢复。

## 安装与更新

正式包和中文说明在 [Releases](https://github.com/h516df/walkpaint/releases)。ARM64 手机选择 `WalkPaint-v<版本>-arm64.apk`，由系统确认覆盖安装，保留已有足迹。不要卸载或清除数据。

0.54 公开版本支持“我的 → 检查更新”，无需 GitHub 登录，仍验证大小、SHA256、包名、递增版本、原签名和架构。0.53 及以前在仓库公开后会拒绝旧私有更新源，首次迁移请在浏览器下载新包覆盖安装。公开准备完成前，0.54仍是未发布候选。

## 从源码构建

需要 JDK21、Android SDK36，使用 Gradle Wrapper；Windows 使用 gradlew.bat。

```sh
./gradlew :app:assembleDebug -PwalkpaintDeveloperBuild=true
./gradlew :app:testDebugUnitTest -PwalkpaintDeveloperBuild=true
python -m unittest discover -s tests -v
python -m unittest discover -s scripts -p 'test_*.py' -v
```

开发模式无需正式私钥，生成独立的 cn.walkpaint.dev 调试 App，不覆盖正式版；开发版从源码更新。没有高德 Key 时使用备用地图路径，见 [构建说明](docs/BUILD.md) 和 [地图配置](docs/AMap_SETUP.md)。正式发布保持原签名。

## 隐私与许可

足迹库和备份保存在本机。在线地图会按相应告知处理必要信息；主动检查更新会访问 GitHub，详见 [隐私说明](PRIVACY.md)。不要提交个人路线、凭据、签名文件或未经检查的截图，见 [安全说明](SECURITY.md)。

第三方 SDK 和数据保留各自条款，见 [第三方声明](NOTICE.md)。项目许可待维护者确认，公开前加入 LICENSE，当前候选不代表开源授权已经完成。

## 已知限制

- GPS受信号、权限、强制停止和厂商后台策略影响，不能保证永不断录。
- 模拟器证据不代替实体手机户外、锁屏、耗电和流畅度验收；缩放仍有长帧。
- 数据库版本为3，回退也须同签名、兼容数据结构且版本号不降低，不能直接装旧包。
- 本机自动备份不能防卸载或丢手机；达到容量预算时保留旧快照，不自动删除。

测试只用公开地点或合成夹具，不接受提交个人轨迹作为测试数据。
