# 原签名与云端交付准备（2026-10-22）

本次用户明确授权迁移原签名，覆盖历史“私钥只留本机”的限制；禁止换证书、合并、正式发布或改变公开下载入口。现有业务 PR 不被本准备分支替代。

包名 `cn.walkpaint`；原证书 SHA256 `21b74e3e2a10e12da8284d4e6bd7d99e55b00a385215902e94d7896f7a46c967`。
已实际下载 `v0.68` 的 APK，核对大小、SHA256、包名与证书；本机原私钥完成随机挑战签名并验证。

## 凭据隔离

原材料只写入 GitHub Environment `android-signing`：`WALKPAINT_KEYSTORE_BASE64`、`WALKPAINT_STORE_PASSWORD`、`WALKPAINT_KEY_PASSWORD`。Base64 仍是私钥。环境只允许 main 和本准备分支，不允许 PR 合并 ref 或任意功能分支；普通 PR 工作流无环境、无正式 Secrets 引用。

`Original signing readiness` 仅手动运行或由本受限准备分支推送触发。无正式凭据的 product 作业编译当前源码的 debug 和 unsigned release；单独受保护的 signing 作业核对同次运行、同提交的候选，再用原证书签署真实产品 APK。验收 APK 与公开哈希回执只保存 3 天，不创建 Release 或更新下载通道，密钥不进缓存或附件。另用合成 APK 验证缺项、错误密码和错误证书拒绝；该探针不能替代真实产品构建和数据验收。运行结果以对应提交 Actions 为准。

开发包使用独立应用 ID，正式包仍保留原包名。正式 release 构建不读取本机 debug.keystore 或仓库 keystore.properties；普通 debug 仍使用 Android 开发签名，不能冒充正式升级包。正式构建未显式传 `-PunsignedRelease=true` 就拒绝；该参数只生成 unsigned 候选。最终签名通过 `cloud/sign_apk.py --input <unsigned.apk> --output <new.apk>`，只从 SIGNING_KEYSTORE_BASE64 / SIGNING_STORE_PASSWORD / SIGNING_KEY_PASSWORD 环境变量读取原身份，先验证证书、私钥、包名及非调试标记。缺项或错误时不交付 APK。

## 备份与恢复边界

GitHub Secrets 不可导出，不能是唯一副本。仓库外独立 DPAPI 加密备份已完成恢复到临时目录、逐字节比对、证书及私钥挑战验证。该副本依赖原 Windows 用户 DPAPI 配置；跨机器、Windows 账户丢失后的长期灾难恢复尚未验证，不能据此删除原文件或原用户配置。具体本地路径仅在本次本地交付报告中保留。

## 未验收

本次没有安装/卸载 App、合并或发布。数据库/JSON 版本迁移、旧备份到新版本恢复、图片视频导入失败、空间不足、进程中断及恢复、系统覆盖安装和真机数据保留均需分别验证，原证书一致不代表这些场景通过。测试只用合成资料。

## 2026-10-23 接续与可移植恢复

真实产品构建单独使用受分支限制的 `android-build` Environment，只有复用原配置的 `WALKPAINT_AMAP_APPLICATION_KEY`，没有原签名私钥。地图配置通过 `ORG_GRADLE_PROJECT_walkpaintAmapKey` 环境注入，正式候选保留 ARM64 和地图 Key 必需检查；普通开发包继续无生产配置构建。APK 编译或签名成功不代表地图加载、GPS 或高德后台授权已经验收。

新增 AES-256-GCM 恢复格式 `H516AR01`，包含原 keystore 的完整字节、原密码与身份声明；解密及验证仅需 JDK 21，不依赖 Windows 用户、DPAPI 或原电脑。加密包保存在仓库之外的私人备份存储，原材料和旧 DPAPI 副本均保留。解密凭据单独保存于 `android-signing` Environment 的 `WALKPAINT_RECOVERY_KEY_BASE64`，不与加密包同存；本机另有受保护的凭据回退副本，不能把该回退副本当作独立跨机器保障。

恢复路径：有权限的代理从私人备份存储取回加密文件、核对加密文件 SHA256，以安全 API 将**加密文件**送入本仓库可信准备分支的 `Original signing readiness` 手动工作流的 `archive_base64` 输入。工作流从受保护环境注入解密凭据，在一次性 Linux 机器内存中解密，比较完整 keystore 摘要、固定证书和包名，并使用恢复的原私钥完成随机挑战。只记录公开摘要和结果，不输出原材料，不上传恢复后的密钥。`cloud/RecoveryVerify.java` 也可由隔离 JDK 环境调用；解密凭据只能经环境安全注入。输入损坏或错误凭据时拒绝恢复。

加密格式可移植、本机恢复成功、云端副本读回成功、独立云机器实际恢复是四项不同证据；本机检查不能代替跨机器恢复。私有 Actions 若被付款／支出限制拦截，记为未运行，不能据此删除原材料。GitHub 环境访问和私人备份存储访问均须可恢复；两项云账号访问丢失的灾难场景尚未验收。实际结果和运行链接见本 PR 的交接。
