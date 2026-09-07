<div align="center">

# 🍪 Cookie Client

**一个用纯 Java 编写的 Minecraft 1.20.1 Fabric PVP 客户端**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3c8e3c?style=flat-square)](https://minecraft.net)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.19.3-db9c5c?style=flat-square)](https://fabricmc.net)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.92.11%2B1.20.1-bf616a?style=flat-square)](https://modrinth.com/mod/fabric-api)
[![Java](https://img.shields.io/badge/Java-17%2B-007396?style=flat-square)](https://adoptium.net)
[![Version](https://img.shields.io/badge/Version-beta1.0-9a4dff?style=flat-square)](./gradle.properties)
[![License](https://img.shields.io/badge/License-CC0--1.0-6c757d?style=flat-square)](./LICENSE)

[English](README.md) · **简体中文**

</div>

---

## 项目简介

**Cookie Client** 是一个基于 **Fabric** 模组加载器的 Minecraft **1.20.1** PVP 客户端，全部使用 **Java** 编写。整体架构参考经典开源客户端 [OpenZen](https://github.com/OpenZenTeam/OpenZen)，HUD / ESP / 通知 / 主题体系是对 [OpenOpal](https://github.com/Opal-Client/OpenOpal) 元素的忠实移植，自定义主菜单为 Setsuna 菜单的高保真移植。

项目尤其注重渲染质量：从 ClickGUI 面板、HUD 行到主菜单，全部使用真实栅格字体、超采样抗锯齿软阴影与缓动动画绘制。目前处于 **beta 早期阶段**：框架、GUI、HUD 与渲染栈已经就位，部分玩法模块仍是骨架。

> ⚠️ **状态：beta（`beta1.0`），持续快速迭代中。** 下表中部分模块仍是占位骨架（逻辑尚未实现）。欢迎 Star / Watch 关注进度。

---

## ✨ 特性

### ClickGUI（`NewClickGui`）

- 按 **7 大分类**（Combat / Movement / Player / Render / Exploit / World / Misc）横排面板，每面板宽 120。
- 打开/关闭**等比缩放动画**（BACK_OUT 曲线；整体 0.8 缩放，以标题行与屏幕中心为锚）。
- 每模块完整设置界面：开关、数值滑条、多选、单选模式、**RGBA 取色器**（滑块 + 实时预览 + 展开动画）与 **按键绑定** 条目。
- 绑定支持键盘键 **与鼠标侧键**（Mouse 4–8）：游戏中按侧键直接开关模块，GUI 绑定元素处于监听态时按侧键即可绑定。
- 右下角常驻圆形 **「E」折叠按钮**（与面板同款软阴影与动画）：按下后面板隐藏（非关闭，Screen 与按钮保留），再按一次展开。

### HUD 体系（OpenOpal 忠实移植）

| 元素 | 说明 |
| :--- | :--- |
| **ModuleList** | 右缘已启用模块阵列（支持 `Side: Left` 左缘镜像）。主题渐变行色、400ms 滑入 / 600ms 重排缓动、动画指示条、`Bar mode`（左/右/无）、`Lowercase`、`Show suffix`、按分类的 `Visible categories`、`Background opacity`，以及 **50–150% 整列 `Scale`**。ClickGUI 折叠时列表显示白色编辑框，可**在屏幕内拖动整列**（边框不出屏）。 |
| **Client Elements** | 左下自底向上堆叠的 **XYZ / BPS / FPS**（每项独立开关、`Lowercase` 选项）与右下**药水状态**列表（原版图标 + 效果色文字 + 时长）。 |
| **Notifications** | 右下角通知队列（滑入/滑出动画、图标、圆角半透明背景卡），可选「模块切换时通知」。 |
| **Theme** | 驱动全局 HUD 渐变色的主题模块：预设（默认 Opal）、**Custom**（双色，可在 ClickGUI 取色器里实时改）与 **Rainbow**。 |

### 自定义主菜单与 Alt 管理

- Setsuna 风格**主页面**（渐变双字段 + 中心交互工具环）默认替换原版标题屏；通过中心环 / 原版屏右上 **COOKIE UI** 按钮可切回原版。
- **Alt 管理**：可滚动账户卡片网格——单击选中、双击登录、右键删除、滚轮翻页、Enter 提交离线名/令牌、Tab 切换输入框。支持**离线账户**与 **Microsoft（令牌）** 账户。

### 渲染模块

- **ESP** 双模式：**Glow** 与 **Opal**。Opal 模式为 OpenOpal ESP 完整移植——2D 框 / 描边 / 血条 / 名牌（模糊底、可选名牌元素与指示器）/ 目标过滤（玩家/怪物/动物/掉落物/箭），走世界→屏幕投影定位，实体追踪准确。
- **Animations**——1.7 格挡手感（1.7 / 1.8 / Rub / Stella / Bounce / Diagonal / Swank 格挡动画 + 主手缩放/位移）。
- **AspectRatio**、**FullBright**（亮度 0–100）、**NameProtect**、**Theme**。

### 渲染基础设施

- 自研 TTF 字体渲染器（`render/`）：字形图谱**超采样渲染 + 双线性过滤**、柔和投影、按真实墨迹精确居中；内置 Product Sans、Axiforma、Material Icons、菜单用 PF 与 Genshin 字体。
- GPU 友好的 SDF 软阴影 / 圆角矩形、模糊底名牌、后屏缓冲模糊工具。
- **Mojang 官方映射** + Mixin 钩子（游戏刻、键鼠、HUD 覆盖、名牌、投影、标题屏…）。

### 其他

- **配置系统**：模块状态（`modules.json`）、设置值（`values.json`）、按键绑定（`binds.json`）持久化在 `<运行目录>/cookie-client/`；ClickGUI 关闭与改键时自动保存。
- **指令**（前缀 `.`）：`.toggle <模块>`、`.bind <模块> <键名>`（键盘键或 `mouse4`–`mouse8`）、`.config save|load`。
- 轻量 **事件总线** 与 Manager 架构（Module / Command / Config / Target / Notification）。

---

## 🧩 模块一览

图例：✅ 已实现 · 🚧 骨架（占位，逻辑尚未实现）

**Combat（战斗）**

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| AutoClicker | ✅ | 左右键自动连点，CPS 1–20、Modern Delay、按住才点 |
| KillAura | 🚧 | 自动攻击附近实体（默认键 `R`） |
| AntiKB | 🚧 | 减小 / 取消受击击退 |

**Movement（移动）**

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| Sprint | ✅ | 自动疾跑（移植自 OpenZen） |
| Scaffold | ✅ | 自动搭桥：Eagle / Sneak / Snap / Rotation Tick / Clutch |
| NoDelay | ✅ | 连跳无跳跃延迟（`LivingEntityMixin` 实现） |
| GuiMove | ✅ | ClickGUI 打开时仍可移动（`KeyboardInputMixin` 实现） |

**Player（玩家）**

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| NoFall | 🚧 | 免摔落伤害（Packet 方案） |

**Render（渲染）**

| 模块 | 状态 | 说明 |
| :--- | :--: | :--- |
| ESP | ✅ | Glow / Opal 双模式，分类型过滤 |
| Animations | ✅ | 自定义格挡动画与主手变换 |
| AspectRatio | ✅ | 自定义投影宽高比（`GameRendererMixin` 实现） |
| FullBright | ✅ | 恒定亮度（0–100 可调） |
| NameProtect | ✅ | 屏幕昵称保护（Fixed / Random） |
| HUD 基础设施 | ✅ | `ModuleList` / `Client Elements` / `Notifications` / `Theme` —— 默认不进阵列列表 |

**World / Exploit / Misc（世界 / 利用 / 杂项）**

| 模块 | 分类 | 状态 | 说明 |
| :--- | :--- | :--: | :--- |
| AutoTools | World | ✅ | 自动切换工具（Fastest / Durability 优先级、Silent） |
| Disabler | Exploit | 🚧 | 绕过服务器限制 |
| AimAssist | Misc | 🚧 | 平滑准星吸附 |

> 共注册 20 个模块，其中 4 个为 HUD 基础设施（始终可用但不显示在阵列列表）。

---

## 🛠 技术栈

| 项目 | 说明 |
| :--- | :--- |
| Minecraft | `1.20.1` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.92.11+1.20.1` |
| Java（运行） | `17`+（MC 1.20.1 运行所需） |
| JDK（开发） | `21`+ —— Fabric Loom 1.17 要求 JDK 21；CI 用 JDK 25 构建 |
| 构建工具 | Gradle `9.5.1`（wrapper）+ Fabric Loom `1.17-SNAPSHOT` |
| 映射 | Mojang 官方映射（Mojmap） |
| 版本 | `beta1.0` |
| 许可证 | CC0-1.0 |

---

## 🚀 快速开始

### 方式一：安装预编译版本

1. 安装 [Fabric Loader 0.19.3](https://fabricmc.net/use/installer/)，游戏版本选 **1.20.1**。
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api)。
3. 将 `fabric-api-*.jar` 与 `cookie-client-beta1.0.jar` 放入 `.minecraft/mods/`。
4. 启动游戏，按 **右 Shift** 打开 ClickGUI（原版界面打开时不响应）。

### 方式二：从源码构建

```bash
git clone https://github.com/xxliam/cookie-client.git
cd cookie-client

# Windows
./gradlew.bat build
# Linux / macOS
./gradlew build
```

构建前请将 `JAVA_HOME` 指向 **JDK 21+**。产物位于 `build/libs/`：

- `cookie-client-beta1.0.jar` — 可直接使用的成品
- `cookie-client-beta1.0-sources.jar` — 源码包

### 启动调试客户端

```bash
./gradlew runClient
```

开发环境（游戏目录与配置）默认在 `run/`（已被 `.gitignore` 忽略）。客户端配置写入 `<运行目录>/cookie-client/`：

| 文件 | 内容 |
| :--- | :--- |
| `modules.json` | 各模块启用/禁用状态 |
| `values.json` | 各模块设置值 |
| `binds.json` | 各模块按键绑定 |

### 默认按键

| 按键 | 作用 |
| :--- | :--- |
| `右 Shift` | 打开 / 关闭 ClickGUI |
| `R` | 开关 KillAura（默认绑定） |

所有模块绑定均可通过 `.bind <模块> <键名>` 或 ClickGUI 的绑定元素修改（支持键盘键与 `mouse4`–`mouse8`）。

---

## 📁 项目结构

```
cookie-client/
├── src/main/
│   ├── java/xxliam/cookieclient/            # 主源码（Java）
│   │   ├── CookieClient.java                # Mod 入口（ModInitializer）
│   │   ├── command/                         # 指令体系（Command + impl：.toggle/.bind/.config）
│   │   ├── config/                          # modules.json / values.json / binds.json
│   │   ├── event/                           # 事件总线（EventBus + 事件定义）
│   │   ├── exception/                       # 自定义异常
│   │   ├── gui/
│   │   │   ├── NewClickGui.java             # ClickGUI + 右下 E 折叠按钮
│   │   │   ├── newclickgui/                 # 分类面板与设置控件元素
│   │   │   └── mainmenu/                    # Setsuna 式主页面 + Alt 管理
│   │   ├── manager/                         # Module / Command / Config / Target / Notification 管理器
│   │   ├── mixin/                           # Mixin 注入（tick、按键、HUD、投影、标题屏…）
│   │   ├── modules/                         # 功能模块
│   │   │   └── impl/                        # 按分类：combat·exploit·misc·movement·player·render·world
│   │   │       ├── render/hud/              # HUD 模块（ModuleList / ClientElements / Notifications）
│   │   │       └── render/esp/              # Opal ESP 渲染器
│   │   ├── notification/                    # 通知队列（右下角卡片）
│   │   ├── render/                          # Renderer / CustomFont / FontStore / GlyphPage / shader
│   │   ├── settings/                        # 设置项体系（Boolean/Number/Mode/MultiSelect/Color/可见性）
│   │   └── utils/                           # animation / game / math / misc / render / rotation
│   └── resources/
│       ├── fabric.mod.json                  # Mod 元数据
│       ├── cookie-client.mixins.json        # Mixin 注册配置
│       └── assets/cookie-client/            # fonts/ + textures/mainmenu/
├── build.gradle.kts                         # 构建脚本（Fabric Loom）
├── gradle.properties                        # 版本与依赖配置
└── settings.gradle.kts                      # 项目名（与 mod id 一致）
```

---

## 🧩 开发指南

### 新建一个模块

```java
package xxliam.cookieclient.modules.impl.combat;

import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

public class MyModule extends Module {

    private final NumberSetting range = new NumberSetting("Range", 3.0, 1.0, 6.0, 0.1);
    private final BooleanSetting silent = new BooleanSetting("Silent", false);

    public MyModule() {
        // (name, category) 或 (name, category, defaultKeyBind)
        super("MyModule", Category.COMBAT, GLFW.GLFW_KEY_R);
        addSetting(range);
        addSetting(silent);
    }

    @Override
    protected void onEnable() { /* 模块启用 */ }

    @Override
    protected void onDisable() { /* 模块禁用 */ }

    @Override
    public void onTick() { /* 启用期间每游戏刻调用 */ }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) { /* HUD 类渲染 */ }
}
```

然后在 `ModuleManager` 中注册（`CookieClient.MODULE_MANAGER.add(new MyModule())`）。设置项带 `() -> false` 可见性谓词时从 ClickGUI 隐藏（HUD 基础设施用）；`ModeSetting` 遇到未知存档值会自动回落到第一档。

### 常用命令

| 命令 | 作用 |
| :--- | :--- |
| `./gradlew build` | 完整构建并产出 jar |
| `./gradlew runClient` | 启动调试客户端 |
| `./gradlew compileJava` | 仅编译 |
| `./gradlew clean` | 清理构建产物 |

> 构建需要 **JDK 21+**（Loom 1.17 要求）。若上次 daemon 残留 `fileHashes.lock` 导致构建「拒绝访问」，先执行 `./gradlew --stop` 再重试。

---

## 🙏 致谢

- **OpenZen** —— 本项目参照的整体模块/管理器架构。
- **OpenOpal** —— HUD 元素、ESP、通知与主题；渲染参数（配色、字体、字号、间距、缓动）均照搬自原版源码。
- **Setsuna** —— 主菜单与 Alt 管理的视觉设计与动画行为。

---

## ⚠️ 免责声明

本项目仅供**学习与研究**使用。客户端类模组可能违反部分服务器的规则，**请务必在服务器管理员允许的前提下使用**；因使用本客户端导致的封禁或其他后果，由使用者自行承担，与作者无关。

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
