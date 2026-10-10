# #11 私有更新补全

用户在 v0.13 浏览器降级交付后要求把剩余问题做完。本阶段唯一写手继承 ed6ee80；不合并旧 PR、不动记录服务、数据库、定位阈值、私人模拟器或手机。

选 GitHub App 官方 device flow：App 仅请求 Contents read，安装范围仅 walkpaint（repository id 1410040906）；无服务器、无 client secret/private key、无 PC gh 凭据进入 Android。公开 client id 是构建配置，不是秘密。授权结果 Android Keystore AES-GCM 本机保存，备份关闭；注销清除授权与下载。GitHub App 的实际注册/安装/用户批准须账户登录及最后权限确认；未完成不得宣布端到端通过。

用户主动检查才联网。列出私有已发布 releases（包括预览，排除草稿）；读取 release-notes.json，版本和标签一致，匹配平台 APK；必须有 API digest、合理 size，下载后验证 hash、原签名、包名、递增 code、ABI/minSdk，再走 FileProvider / Android 系统安装器。未知来源由用户确认，无自动安装。授权、网络、下载取消/失败可重试，不误报最新；退出页面可取消、销毁取消工作，不启动常驻服务。

验收：JVM 策略/异常测试，Google 专用 AVD 真实 UI/系统入口/旧行包含验证；真实 GitHub 设备授权后私有读取、下载、覆盖安装才算完整渠道验收。vivo 户外/长期后台仍必须本人实际行走，没有真机结果不关闭 #6 等。回退同签名 schema2 且 code >=15 修复包，禁止卸载/清数据。
