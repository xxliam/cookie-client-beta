<div align="center">

# 🍪 Cookie Client

**一个为 Minecraft 1.20.1 打造的 Fabric PVP 客户端**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3c8e3c?style=flat-square&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Loader](https://img.shields.io/badge/Fabric%20Loader-0.19.3-db9c5c?style=flat-square&logo=fabric&logoColor=white)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-CC0--1.0-6c757d?style=flat-square)](./LICENSE)

</div>

---

## 项目简介

**Cookie Client** 是一个基于 **Fabric** 模组加载器的 Minecraft 1.20.1 PVP 客户端，使用 **Java** 编写，项目结构参考经典开源客户端 OpenZen。

项目目标是在保持轻量、高性能的前提下，为 PVP 玩家提供一套可自由开关的战斗辅助模块：精确的手感微调、清晰的信息可视化（HUD）、以及足够灵活的模块系统，让玩家能按自己的习惯配置每一个细节。

> ⚠️ 项目目前处于 **早期开发阶段**，大部分模块尚未实现。欢迎 Star / Watch 关注进度。

---

## ✨ 特性

图例：✅ 已实现 · 🚧 开发中 · 📝 计划中

### 战斗（Combat）

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| Aim Assist | 📝 | 平滑的目标吸附，可调 FOV / 速度 / 曲线 |
| Reach | 📝 | 攻击距离微调 |
| Hit Select | 📝 | 优先级目标选择（距离 / 角度 / 血量） |
| Auto Clicker | 📝 | 可调 CPS 与随机化抖动 |
| Criticals | 📝 | 跳劈 / 包手临界 |
| WTap / STap | 📝 | 击退优化 |

### 视觉（Render）

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| HUD | 🚧 | 可拖拽的模块化 HUD 编辑器 |
| ESP | 📝 | 玩家 / 实体框渲染与信息标签 |
| Name Tags | 📝 | 自定义名牌（血量、距离、装备） |
| Trajectories | 📝 | 弓箭 / 投射物轨迹预测 |
| Fullbright | ✅ | 恒定亮度 |

### 移动（Movement）

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| Sprint / Auto Sprint | 📝 | 自动疾跑 |
| Velocity | 📝 | 受击回退控制 |
| Safe Walk | 📝 | 边缘防掉落 |
| Strafe | 📝 | 转向加速优化 |

### 客户端（Client）

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| ClickGUI | 🚧 | 模块开关与参数配置面板 |
| Config System | 🚧 | 基于 JSON 的配置存档 |
| Command | 🚧 | 客户端指令体系（`.toggle` / `.bind` / `.config`） |
| Friends | 📝 | 好友名单，避免误伤与目标混淆 |

---

## 🛠 技术栈

| 项目 | 版本 |
| :--- | :--- |
| Minecraft | `1.20.1` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.92.11+1.20.1` |
| Java (JDK) | `17` |
| 构建工具 | Gradle + Fabric Loom `1.17` |
| 映射 | Mojang Official Mappings |
| 许可证 | CC0-1.0 |

---

## 🚀 快速开始

### 方式一：安装预编译版本（推荐）

1. 下载并安装 [Fabric Loader 0.19.3](https://fabricmc.net/use/installer/)，游戏版本选择 **1.20.1**
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 将 `fabric-api-*.jar` 与本项目的 `cookie-client-*.jar` 放入 `.minecraft/mods/`
4. 启动游戏，默认按键打开 GUI

### 方式二：从源码构建

```bash
# 克隆仓库
git clone https://github.com/xxliam/cookie-client.git
cd cookie-client

# 构建（Windows）
./gradlew.bat build

# 构建（Linux / macOS）
./gradlew build
```

构建产物位于 `build/libs/`：

- `cookie-client-1.0.0.jar` — 可直接使用的成品
- `cookie-client-1.0.0-sources.jar` — 源码包

### 启动调试客户端

```bash
./gradlew runClient
```

生成环境默认在 `run/` 目录（该目录已被 `.gitignore` 忽略）。

---

## 📁 项目结构

```
cookie-client/
├── src/main/
│   ├── java/xxliam/cookieclient/        # 主源码（Java）
│   │   ├── CookieClient.java            # Mod 入口（ModInitializer）
│   │   ├── command/                     # 指令系统（Command + impl）
│   │   ├── config/                      # 配置存档（modules.json / values.json）
│   │   ├── event/                       # 事件总线（EventBus + 事件定义）
│   │   ├── exception/                   # 自定义异常
│   │   ├── gui/                         # ClickGUI
│   │   ├── hud/                         # HUD 元素
│   │   ├── manager/                     # Module / Command / Config / HUD / Target 管理器
│   │   ├── mixin/                       # Mixin 字节码注入
│   │   ├── modules/                     # 功能模块（impl/ 按分类：combat·exploit·misc·movement·player·render·world）
│   │   ├── render/                      # 渲染工具（Renderer）
│   │   ├── settings/                    # 设置项体系（Boolean / Number / Mode / MultiSelect）
│   │   └── utils/                       # 工具（animation / game / math / misc / render / rotation）
│   └── resources/
│       ├── fabric.mod.json              # Mod 元数据
│       ├── cookie-client.mixins.json    # Mixin 注册配置
│       └── assets/cookie-client/        # 图标与资源
├── build.gradle.kts                     # 构建脚本
├── gradle.properties                    # 版本与依赖配置
└── settings.gradle.kts                  # 项目名（需与 mod id 一致）
```

---

## 🧩 开发指南

### 构建一个新模块

```java
package xxliam.cookieclient.modules.impl.combat;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

public class MyModule extends Module {

    private final NumberSetting range = new NumberSetting("Range", "攻击距离", 3.0, 1.0, 6.0, 0.1);
    private final BooleanSetting silent = new BooleanSetting("Silent", "静默旋转", false);

    public MyModule() {
        super("MyModule", "模块描述", Category.COMBAT);
        addSetting(range);
        addSetting(silent);
    }

    @Override
    protected void onEnable() { /* 模块启用 */ }

    @Override
    protected void onDisable() { /* 模块禁用 */ }

    @Override
    public void onTick() { /* 每游戏刻逻辑 */ }
}
```

### 常用命令

| 命令 | 作用 |
| :--- | :--- |
| `./gradlew build` | 完整构建并产出 jar |
| `./gradlew runClient` | 启动调试客户端 |
| `./gradlew runServer` | 启动调试服务端 |
| `./gradlew clean` | 清理构建产物 |
| `./gradlew genSources` | 生成反编译的 Minecraft 源码（方便查阅） |

### 修改依赖版本

所有版本号集中在 `gradle.properties` 中管理，改完刷新 Gradle 即可：

```properties
minecraft_version=1.20.1
loader_version=0.19.3
fabric_api_version=0.92.11+1.20.1
```

---

## ⚠️ 免责声明

本项目仅供学习与研究使用。客户端类模组可能违反部分服务器的规则，**请务必在服务器管理员允许的前提下使用**；因使用本客户端导致的封禁或其他后果，由使用者自行承担，与作者无关。

---

## 📄 许可证

本项目基于 [CC0-1.0](./LICENSE) 许可证发布（公共领域贡献）。

---

## 👤 作者

**xxliam**

- GitHub：[@xxliam](https://github.com/xxliam)
- 项目仓库：[github.com/xxliam/cookie-client](https://github.com/xxliam/cookie-client)
- Issues：[提交 Bug 与功能建议](https://github.com/xxliam/cookie-client/issues)

欢迎提交 Issue 与 Pull Request，一起把 Cookie Client 做下去 🍪
