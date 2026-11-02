# Gather 构建与验证

最低 Android 8.0 / API 26，compile / target API 35。包名 `com.qianwang.privatewealth` 保留以支持原试用版升级。

## Android Studio / Gradle

使用 JDK 17、Android SDK 35、Build Tools 35.0.0：

```bash
./gradlew :app:assembleDebug
```

Wrapper 固定为 Gradle 8.11.1，AGP 8.9.2。首次构建需要下载依赖。已使用隔离的 JDK 17 与 Android SDK 35 验证 debug 构建及领域检查。Gradle 的 release 默认未配置签名；不要将 unsigned APK 当作可安装发布包。

## 本地离线构建器

这是维护者已实际使用的构建路径。工具放在 `.local/toolchain`，或以 `ANDROID_TOOLS_HOME` 指定根目录：

```text
toolchain/
  ecj.jar
  platform/android.jar
  jdk-17.0.20.1+1-jre/Contents/Home/bin/{java,keytool}
  build-tools/android-15/{aapt,zipalign,lib/d8.jar,lib/apksigner.jar}
```

`android.jar` 使用 API 35。JRE 路径不同时，设置 `GUILONG_JAVA_HOME` 指向含 `bin/java` 与 `bin/keytool` 的目录；保留旧环境变量名以兼容原有本地配置。

```bash
export ANDROID_TOOLS_HOME="$PWD/.local/toolchain"
export GUILONG_JAVA_HOME="$ANDROID_TOOLS_HOME/jdk-17.0.21+9-jre/Contents/Home"
./scripts/build.sh --check-tools
./scripts/build.sh --release
```

发行输出：`app/build/outputs/apk/release/Gather-0.7.3-release.apk`，同目录 JSON 记录版本、字节数及 SHA-256。release APK 禁用调试，沿用 `.local/signing/` 的私有签名身份。首次构建会生成本机身份；维护者原密钥不包含在仓库，其他人构建的签名自然不同。保管好密钥和密码文件，不要提交它们。

不传 `--release` 则生成 debug 变体，便于 `run-as` 设备测试。离线工具不下载或修改全局 SDK/JDK。

## 领域检查

标准 Gradle 入口（JDK 17 和 Android SDK 35）：

```bash
./gradlew :app:domainCheck
```

该入口使用合成数据，在 JVM 执行领域、桑基图及行情／备份检查。新增的 `ledgerStorageCheck` 使用独立临时目录、测试专用的 Context／AtomicFile 替身与固定版本的 JSON 库，实际执行收入入账、每日来源、旧版迁移、备份往返及写入失败回滚路径；这些测试依赖不会进入 APK。JVM 检查不替代 Android 实际存储与界面验证。GitHub Actions 对推送和 PR 运行此检查及 debug 构建。

离线工具链也可运行：

```bash
python3 scripts/test_assets_v6.py
python3 scripts/test_domain.py
python3 scripts/test_sankey.py
./scripts/test-quotes.sh
```

所有测试均支持通过 `GUILONG_JAVA_HOME` 指定 JRE。测试使用合成数据，默认仅在 JVM 运行；两个 Python 领域脚本支持显式 `--serial` 在独立 Android 临时目录检查真实存储，不触碰应用账本。`scripts/test-quotes.sh --live` 会额外请求真实外部行情。

## Android 备份与生命周期回归

配置离线工具及已启动的专用模拟器后：

```bash
python3 scripts/test_backup_android.py --serial emulator-5580
```

可通过 `--adb` / `--adb-port` 指定 ADB。该入口只接受模拟器序列号，构建并安装独立的 `com.qianwang.privatewealth.backupqa` 包，使用合成账本检查异步加密、文件导出、错误密码、恢复确认、失败恢复、单任务限制，以及备份期间页面重建／延迟绘制。结束后卸载测试包，不写入正式应用账本。此回归需要 Android，当前 GitHub CI 只执行 debug 构建和 JVM 领域检查。

## 专用模拟器

设置 `GUILONG_ANDROID_SDK`，其中须含 platform-tools、emulator 及 API 35 ARM64 Google APIs 镜像，然后运行：

```bash
./scripts/start-emulator.sh -no-window
./scripts/adb.sh wait-for-device
./scripts/adb.sh shell getprop sys.boot_completed
```

启动完成返回 `1` 后可以安装 APK。专用 AVD 名称保留 `Guilong`，设备 `emulator-5580`、ADB 端口 5041；可通过 `GUILONG_EMULATOR_PORT` / `GUILONG_ADB_PORT` 覆盖。所有可变状态在被忽略的 `.local/` 中，不会提交到 Git。

## GitHub 正式签名发行（v0.7.3）

正式发行需要**原版签名**，否则不能覆盖安装并保留现有账本。仓库不会保存私钥；发布工作流 `.github/workflows/release.yml` 必须配置以下 **Actions repository secrets**（不是提交代码或在 Issue 中粘贴）：

- `GATHER_SIGNING_P12_BASE64`：原构建环境 `.local/signing/guilong-debug.p12` 的 Base64 文本。macOS／Linux 可在**原构建机**运行 `base64 < .local/signing/guilong-debug.p12 | tr -d '\\n'` 获取。
- `GATHER_SIGNING_PASSWORD`：原构建环境 `.local/signing/password.txt` 的原始内容，不要上传密码文件或把密码发到聊天里。

添加路径：仓库 **Settings → Secrets and variables → Actions → New repository secret**。此后在 **Actions → Publish signed Gather APK → Run workflow** 手动启动，或由主分支相关变更自动触发。缺少原密钥时工作流报错并停止，不会发布不兼容的 APK。

工作流先在 JDK 17 / Android SDK 35 运行构建与领域检查，再构建 Release。正式发布前将下载已发布的 v0.7.1 APK，核对 SHA-256 和新旧 APK 的签名证书，并校验包名、版本号、APK 结构。全部通过后才生成 v0.7.3 GitHub Release，附 APK、SHA256SUMS.txt 与构建元数据。**若原密钥已丢失，不可自行生成替代签名后称其可原位升级。** 升级前请先在旧版本导出加密备份。
