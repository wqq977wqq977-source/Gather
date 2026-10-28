# Gather 构建与验证

最低 Android 8.0 / API 26，compile / target API 35。包名 `com.qianwang.privatewealth` 保留以支持原试用版升级。

## Android Studio / Gradle

使用 JDK 17、Android SDK 35、Build Tools 35.0.0：

```bash
./gradlew :app:assembleDebug
```

Wrapper 固定为 Gradle 8.11.1，AGP 8.9.2。首次构建需要下载依赖。标准 Gradle 路径尚未在维护者本机验证。Gradle 的 release 默认未配置签名；不要将 unsigned APK 当作可安装发布包。

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

发行输出：`app/build/outputs/apk/release/Gather-0.7.0-release.apk`，同目录 JSON 记录版本、字节数及 SHA-256。release APK 禁用调试，沿用 `.local/signing/` 的私有签名身份。首次构建会生成本机身份；维护者原密钥不包含在仓库，其他人构建的签名自然不同。保管好密钥和密码文件，不要提交它们。

不传 `--release` 则生成 debug 变体，便于 `run-as` 设备测试。离线工具不下载或修改全局 SDK/JDK。

## 领域检查

配置上述工具后：

```bash
python3 scripts/test_assets_v6.py
python3 scripts/test_domain.py
python3 scripts/test_sankey.py
./scripts/test-quotes.sh
```

Sankey 测试当前使用工具目录下默认 JRE 路径；可在该位置配置兼容的 JRE。测试使用合成数据，默认仅在 JVM 运行；两个 Python 领域脚本支持显式 `--serial` 在独立 Android 临时目录检查真实存储，不触碰应用账本。`scripts/test-quotes.sh --live` 会额外请求真实外部行情。

## 专用模拟器

设置 `GUILONG_ANDROID_SDK`，其中须含 platform-tools、emulator 及 API 35 ARM64 Google APIs 镜像，然后运行：

```bash
./scripts/start-emulator.sh -no-window
./scripts/adb.sh wait-for-device
./scripts/adb.sh shell getprop sys.boot_completed
```

启动完成返回 `1` 后可以安装 APK。专用 AVD 名称保留 `Guilong`，设备 `emulator-5580`、ADB 端口 5041；可通过 `GUILONG_EMULATOR_PORT` / `GUILONG_ADB_PORT` 覆盖。所有可变状态在被忽略的 `.local/` 中，不会提交到 Git。
