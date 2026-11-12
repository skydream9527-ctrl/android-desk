# AndroidDesk · 构建与验证边界

| 项 | 内容 |
|---|---|
| 更新日期 | 2026-11-12 |
| 对应文档 | [开发方案.md](../方案/开发方案.md)（逐文件状态、工作包）· [重构决策.md](../方案/重构决策.md) · [待实测清单.md](../方案/待实测清单.md) |
| 当前阶段 | **核心模型已测，应用骨架已编译、未实机验证。** 现在打出的 APK 启动即崩（入口 Activity 仍是占位） |

---

## 1. 原则：只在 GitHub 上构建和打包

**不要在本地搭建本项目的构建或测试环境。** 不要为了编译、单测或出包而安装 JDK、Android SDK，不要创建 `local.properties`，也不要在 Android Studio 里本地打包，或在本地安装模拟器来当测试环境。

单测、组装和 APK（`:app`、W0 工具包等）一律在 GitHub 上完成（GitHub Actions 或仓库提供的其他托管流程）。需要在真机上安装或做 W0 实测时，只使用 GitHub 产出的制品，不得在本地编译出包。

## 2. 构建环境（仅用于 GitHub 上的构建）

下面这些是 GitHub 构建应使用的版本，不要求、也不应在本机准备：

| 项 | 值 |
|---|---|
| JDK | 17 及以上（基线在 Temurin 21 上验证）。字节码目标是 17，不依赖 toolchain 自动下载 |
| Gradle | 8.11.1，用仓库里的 wrapper |
| Android SDK | 平台 35、build-tools 35 |
| Android Gradle Plugin / Kotlin / Compose BOM | 8.7.3 / 2.0.21 / 2024.12.01（见 `gradle/libs.versions.toml`） |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |

## 3. GitHub 上运行的命令

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

这些命令只在 GitHub 上跑，不要在本地执行。

## 4. 基线结果（2026-11-12）

| 命令 | 结果 |
|---|---|
| `testAll` | 通过，44 项（core、host、client） |
| `:app:assembleDebug` | 通过，约 9 MB |
| `:app:lintDebug` | 失败，1 个 error：`LauncherActivity must extend android.app.Activity`。入口 Activity 写好后消失 |

没有在任何设备上运行过。

## 5. 做到哪一步

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

## 6. 真机安装

不要在本地准备模拟器镜像，也不要用 `sdkmanager` / `avdmanager` 搭建测试环境。需要验证时，从 GitHub 下载已打包的 APK，安装到真机：

```bash
adb install -r <从 GitHub 下载的 app-debug.apk>
```

```bash
adb shell am start -W -n com.skydream.androiddesk/.app.LauncherActivity
```

## 7. W0 实测

`w0probe/README.md` 记录每项怎么跑。结果放到 `w0probe/results/<日期>/`。在真机结论出来之前，不要把「已支持自由窗口」写进设置界面。W0 工具包如需出包，同样在 GitHub 上完成。
