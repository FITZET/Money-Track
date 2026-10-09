# Security

## 不应提交到公开仓库的文件

- `keystore.properties`
- `*.jks`、`*.keystore`、`*.p12`、`*.pem`、`*.key`
- `*.db`、`*.sqlite`、`*.mtbackup`
- APK、AAB、`build/`、`.gradle/` 和本机 Android Studio 配置

发布签名密钥必须保存在公开仓库之外，并至少保留一份离线备份。GitHub Actions 如需签名，应使用加密的 Actions Secrets，在构建时临时还原密钥，构建结束后删除。

## 报告安全问题

在建立公开仓库后，请配置 GitHub Security Advisories，用私密报告渠道接收可能涉及通知读取、备份恢复、文件共享或数据库处理的问题。不要在公开 Issue 中附带真实账单、通知截图、备份文件或签名材料。
