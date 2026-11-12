# AndroidDesk · 构建与验证边界

| 项 | 内容 |
|---|---|
| 更新日期 | 2026-11-12 |
| 对应文档 | [开发方案.md](../方案/开发方案.md)（逐文件状态、工作包）· [重构决策.md](../方案/重构决策.md) · [待实测清单.md](../方案/待实测清单.md) |
| 当前阶段 | **核心模型已测，应用骨架已编译、未实机验证。** 现在打出的 APK 启动即崩（入口 Activity 仍是占位） |

---

## 1. 环境

| 项 | 值 |
|---|---|
| JDK | 17 及以上（基线在 Temurin 21 上验证）。字节码目标是 17，不依赖 toolchain 自动下载 |
| Gradle | 8.11.1，用仓库里的 wrapper |
| Android SDK | 平台 35、build-tools 35 |
| Android Gradle Plugin / Kotlin / Compose BOM | 8.7.3 / 2.0.21 / 2024.12.01（见 `gradle/libs.versions.toml`） |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |

首次构建前，在本目录创建 `local.properties`（已被 `.gitignore` 忽略）：

```properties
sdk.dir=/你的/Android/sdk/路径
```

## 2. 命令

纯 Kotlin 模块的单测（不需要 Android SDK 以外的东西）：

```bash
./gradlew testAll
```

打调试包：

```bash
./gradlew :app:assembleDebug
```

静态检查：

```bash
./gradlew :app:lintDebug
```

## 3. 基线结果（2026-11-12）

| 命令 | 结果 |
|---|---|
| `testAll` | 通过，44 项（core、host、client） |
| `:app:assembleDebug` | 通过，约 9 MB |
| `:app:lintDebug` | 失败，1 个 error：`LauncherActivity must extend android.app.Activity`。入口 Activity 写好后消失 |

没有在任何设备或模拟器上运行过。基线机器上没有模拟器镜像。

## 4. 做到哪一步

| 部分 | 状态 |
|---|---|
| `core`：布局、网格尺寸、图标样式、助手指令、确认闸门、存储格式 | 已实现并测试 |
| `host`：助手注册、协议解析 | 已实现并测试 |
| `sdk`：助手协议 v1 常量 | 已定 |
| `app`：状态控制器、应用目录、图标渲染、存储、助手入口、桌面画布 | 已写，能编译，**未实机验证** |
| `app`：入口 Activity、根界面、抽屉、应用面板、应用管理、图标样式页、设置、助手层界面 | 未实现 |
| `sample-agent` | 未实现 |
| 自由窗口启动 | 不实现，等 W0-T2 |
| 虚拟屏合成 | 不实现，等 T3 通过且单独评审 |
| 无障碍注入（在其他应用里点按 / 输入） | 不实现，等 W0-T4 |

后三项留空的原因：阻塞级实测没有结论时先写实现，容易整段推翻。本轮的人机协同只作用于桌面自身，不经无障碍，不在此限。

## 5. 模拟器

接手后先准备一台手机和一台平板模拟器（开发方案 WP0）。`sdkmanager` 与 `avdmanager` 在 SDK 的 `cmdline-tools/latest/bin/` 下；Apple 芯片用 `arm64-v8a`，Intel 用 `x86_64`。

```bash
sdkmanager "system-images;android-35;google_apis;arm64-v8a"
```

```bash
avdmanager create avd -n desk_phone -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_8
```

```bash
avdmanager create avd -n desk_tablet -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_tablet
```

装好后安装并启动：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb shell am start -W -n com.skydream.androiddesk/.app.LauncherActivity
```

## 6. W0 实测

`w0probe/README.md` 记录每项怎么跑。结果放到 `w0probe/results/<日期>/`。在真机结论出来之前，不要把「已支持自由窗口」写进设置界面。
