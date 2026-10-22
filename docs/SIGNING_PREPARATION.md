# 原签名与云端交付准备（2026-10-22）

本次用户明确授权迁移原签名，覆盖历史“私钥只留本机”的限制；禁止换证书、合并、正式发布或改变公开下载入口。现有业务 PR 不被本准备分支替代。

包名 `cn.walkpaint`；原证书 SHA256 `21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967`。
已实际下载 `v0.68` 的 APK，核对大小、SHA256、包名与证书；本机原私钥完成随机挑战签名并验证。

## 凭据隔离

原材料只写入 GitHub Environment `android-signing`：`WALKPAINT_KEYSTORE_BASE64`、`WALKPAINT_STORE_PASSWORD`、`WALKPAINT_KEY_PASSWORD`。Base64 仍是私钥。环境只允许 main 和本准备分支，不允许 PR 合并 ref 或任意功能分支；普通 PR 工作流无环境、无正式 Secrets 引用。

`Original signing readiness` 仅手动运行或由本受限准备分支推送触发：在隔离作业中签署无真实资料的合成 APK，并验证缺项、错误密码和错误证书均失败；APK 和密钥均不上传附件、不发布，临时材料退出时删除。配置存在不代表运行通过，实际结果看本 PR 的 Actions。

开发包使用独立应用 ID，正式包仍保留原包名。Gradle 不读取本机 debug.keystore 或仓库 keystore.properties。正式构建未显式传 `-PunsignedRelease=true` 就拒绝；该参数只生成 unsigned 候选。最终签名通过 `cloud/sign_apk.py --input <unsigned.apk> --output <new.apk>`，只从 SIGNING_KEYSTORE_BASE64 / SIGNING_STORE_PASSWORD / SIGNING_KEY_PASSWORD 环境变量读取原身份，先验证证书、私钥、包名及非调试标记。缺项或错误时不交付 APK。

## 备份与恢复边界

GitHub Secrets 不可导出，不能是唯一副本。仓库外独立 DPAPI 加密备份已完成恢复到临时目录、逐字节比对、证书及私钥挑战验证。该副本依赖原 Windows 用户 DPAPI 配置；跨机器、Windows 账户丢失后的长期灾难恢复尚未验证，不能据此删除原文件或原用户配置。具体本地路径仅在本次本地交付报告中保留。

## 未验收

本次没有安装/卸载 App、合并或发布。数据库/JSON 版本迁移、旧备份到新版本恢复、图片视频导入失败、空间不足、进程中断及恢复、系统覆盖安装和真机数据保留均需分别验证，原证书一致不代表这些场景通过。测试只用合成资料。
