# 签名与升级红线

WalkPaint 的每一版安装包都用同一把 debug 密钥签名。**换签名是最不可逆的操作**：手机上的旧
App 无法覆盖升级，唯一的路是卸载重装，而卸载等于丢掉全部历史足迹。所以签名这件事在构建期
就设了硬门禁，而不是靠人记得。

## 硬门禁

`app/build.gradle.kts` 在配置阶段检查两件事，任一不满足就构建失败（退出码非 0）：

1. `$HOME/.android/debug.keystore` 存在；
2. 它的证书 SHA1 等于 `FA:E3:DF:35:B3:2F:A7:DF:41:B3:F8:53:79:3E:A6:9D:39:5B:8E:52`。

失败信息以 `[walkpaint-signing]` 开头。旧版本是 `if (existing.isFile)`，密钥文件不在时静默跳过
签名配置，AGP 会自己生成一把新密钥，构建照样绿灯，只有装到手机上才炸。现在不会了。

可覆盖项：

| 参数 | 作用 |
| --- | --- |
| `-PwalkpaintSigningKeystore=<path>` | 换一个密钥库路径（用于验证门禁本身） |
| `-PallowNewDebugKey=true` | 显式放行"没有历史密钥"，用于全新机器开新的签名起点。**分发前不要用** |

## 高德 Key 同样有门禁

Key 缺失（空值）或格式不是 32 位字母数字都会让构建失败，失败信息以 `[walkpaint-amap-key]` 开头。
旧版本的正则是 `[A-Za-z0-9]*`，空 Key 也放行，只在手机上打开地图时才报错。确实要构建无 Key 包
（只能走离线或备用底图）时加 `-PallowMissingAmapKey=true`。

Key 的值来自 Git 忽略的 `local.properties` 的 `walkpaintAmapKey`，或被 `-PwalkpaintAmapKey=` 覆盖。
构建日志只输出长度，不输出值。含 Key 的 APK 不要上传为 Release 资产。

## 备份

密钥本体不在仓库里，本机有两份备份：

- `D:\Backups\WalkPaint-signing\debug.keystore`
- `C:\Users\My\Documents\WalkPaint-signing-backup\debug.keystore`

两份与 `C:\Users\My\.android\debug.keystore` 的 SHA256 必须同为
`78ED4073232B2761F183687F4435C794CF8A26EC77BB7688EC69BD60322B2419`。恢复步骤和自查命令见
`D:\Backups\WalkPaint-signing\README.md`。

**这两份都在同一台电脑上，还不是真正的离线备份。** 需要人再放一份到 U 盘或网盘。

## 验收

```powershell
# 门禁本身
pwsh -NoProfile -File <workspace>\walkpaint-review-20261010\verify\signing_guard.ps1

# 产物签名（端到端）
pwsh -NoProfile -File <workspace>\walkpaint-review-20261010\verify\apk_signature_guard.ps1
```

`apk_signature_guard.ps1` 会真的构建 `app-debug.apk` 并用 `apksigner` 核对证书指纹，
不是只看配置阶段。
