<div align="center">

# 🍪 Cookie Client

**A Fabric PVP client for Minecraft 1.20.1, written in pure Java**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3c8e3c?style=flat-square)](https://minecraft.net)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.19.3-db9c5c?style=flat-square)](https://fabricmc.net)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.92.11%2B1.20.1-bf616a?style=flat-square)](https://modrinth.com/mod/fabric-api)
[![Java](https://img.shields.io/badge/Java-17%2B-007396?style=flat-square)](https://adoptium.net)
[![Version](https://img.shields.io/badge/Version-beta1.0-9a4dff?style=flat-square)](./gradle.properties)
[![License](https://img.shields.io/badge/License-CC0--1.0-6c757d?style=flat-square)](./LICENSE)

**English** · [简体中文](README.zh-CN.md)

</div>

---

## Overview

**Cookie Client** is a Minecraft **1.20.1** PVP client built on the **Fabric** mod loader and written entirely in **Java**. The overall architecture follows the classic open-source client [OpenZen](https://github.com/OpenZenTeam/OpenZen) (`shit.zen` package layout), while the HUD / ESP / notification / theme systems are faithful ports of [OpenOpal](https://github.com/Opal-Client/OpenOpal) elements and the custom main menu is a high-fidelity port of the Setsuna menu.

It is developed with a strong focus on rendering quality — everything from the ClickGUI panels to HUD rows and the main menu is drawn with real raster fonts, supersampled/anti-aliased soft shadows and eased animations. The project is still in an early **beta** stage: the framework, GUI, HUD and rendering stack are in place, while a number of gameplay modules are still scaffolding.

> ⚠️ **Status: beta (`beta1.0`), heavy active development.** Some modules listed below are placeholders whose logic has not been implemented yet. Join the progress via Star / Watch.

---

## ✨ Highlights

### ClickGUI (`NewClickGui`)

- Category panels for all **7 categories** (Combat / Movement / Player / Render / Exploit / World / Misc), 120px wide each.
- Open/close **scale animation** (BACK_OUT curve, 0.8 overall scale anchored to the title row and screen center).
- Full settings UI per module: booleans, number sliders, mode selection, multi-select, **RGBA color picker** (sliders with live preview + expand animation) and **key-bind** entries.
- Binding supports keyboard keys **and mouse side buttons** (Mouse 4–8), both in-game (switch module) and while the ClickGUI bind element is listening.
- A round **"E" collapse button** sits at the bottom-right corner (same soft shadow & animation as the panels): it hides the panels instead of closing the screen, and re-shows them on a second press.

### HUD system (faithful OpenOpal port)

| Element | Description |
| :--- | :--- |
| **ModuleList** | Enabled-module array list on the right edge (`Side: Left` mirror mode available). Theme-gradient row colors, 400ms slide-in / 600ms reflow easing, animated bar, `Bar mode` (left/right/none), `Lowercase`, `Show suffix`, per-category `Visible categories`, `Background opacity` and a **50–150% column `Scale`**. When the ClickGUI is collapsed you can **drag the whole list** inside the screen bounds and it shows a white editing outline. |
| **Client Elements** | Bottom-left stacked **XYZ / BPS / FPS** (each independently toggleable, `Lowercase` option) and bottom-right **potion/status effect** list with vanilla icons and colored names + durations. |
| **Notifications** | Animated bottom-right toast cards (slide in/out, icons, rounded translucent background). Optional "on module toggle" notifications. |
| **Theme** | Global accent theme driving the HUD gradients — presets (Opal default), **Custom** (two colors, editable live in the ClickGUI color pickers) and **Rainbow**. |

### Custom main menu & Alt Manager

- Setsuna-style **main menu** (gradient fields + center interaction ring) replaces the vanilla title screen by default; switch back to the vanilla title via the ring / the **COOKIE UI** button.
- **Alt Manager**: scrollable account card grid — single-click select, double-click login, right-click delete, Enter to commit an offline name or token, Tab to switch inputs. Supports **offline** and **Microsoft (token)** accounts.

### Render modules

- **ESP** with two modes: **Glow** and **Opal**. The Opal mode is a full port of OpenOpal's ESP — 2D box / box stroke / health bar / name tags (blur-backed, with selectable name-tag elements & indicators) / target filters (players / mobs / animals / items / arrows), positioned through world→screen projection so it tracks entities correctly.
- **Animations** — old-school 1.7 block-hitting feel (1.7 / 1.8 / Rub / Stella / Bounce / Diagonal / Swank block animations plus hand scale / offsets).
- **AspectRatio**, **FullBright** (brightness 0–100), **NameProtect**, **Theme**.

### Rendering foundation

- Custom TTF font renderer (`render/`): glyph atlas with **supersampled rendering + bilinear filtering**, soft drop shadows and exact ink-bounds centering — bundled fonts include Product Sans, Axiforma, Material Icons, PF (used by the menu) and Genshin.
- Soft shadows / rounded rects via GPU-friendly SDF rendering, blur-backed name tags and back-buffer blur utilities.
- **Mojang official mappings** + Mixin-based hooks (tick, keyboard/mouse, HUD overlay, name labels, projections, title screen…).

### Everything else

- **Config system**: module states (`modules.json`), setting values (`values.json`) and key binds (`binds.json`) are persisted in the `<run-dir>/cookie-client/` folder; saving happens on ClickGUI close and key-bind changes.
- **Commands** (prefix `.`): `.toggle <module>`, `.bind <module> <key>` (keyboard keys or `mouse4`–`mouse8`), `.config save|load`.
- Lightweight **event bus** + manager architecture (Module / Command / Config / Target / Notification).

---

## 🧩 Module list

Legend: ✅ implemented · 🚧 scaffolding (skeleton, logic not implemented yet)

**Combat**

| Module | State | Description |
| :--- | :--: | :--- |
| AutoClicker | ✅ | Left / right auto clicker, CPS 1–20, "Modern Delay" timing, require-pressed option |
| KillAura | 🚧 | Auto-attack nearby entities (default bind `R`) |
| AntiKB | 🚧 | Reduce / cancel knockback |

**Movement**

| Module | State | Description |
| :--- | :--: | :--- |
| Sprint | ✅ | Auto sprint (ported from OpenZen) |
| Scaffold | ✅ | Automatic bridging: Eagle / Sneak / Snap / Rotation Tick / Clutch |
| NoDelay | ✅ | Remove jump delay while bunny-hopping (via `LivingEntityMixin`) |
| GuiMove | ✅ | Keep moving while the ClickGUI is open (via `KeyboardInputMixin`) |

**Player**

| Module | State | Description |
| :--- | :--: | :--- |
| NoFall | 🚧 | Avoid fall damage (packet-based) |

**Render**

| Module | State | Description |
| :--- | :--: | :--- |
| ESP | ✅ | Glow / Opal modes with per-type filters |
| Animations | ✅ | Custom block-hit animation & hand transforms |
| AspectRatio | ✅ | Custom projection aspect ratio (via `GameRendererMixin`) |
| FullBright | ✅ | Constant brightness (0–100) |
| NameProtect | ✅ | Protect your own name on screen (Fixed / Random) |
| HUD infra | ✅ | `ModuleList`, `Client Elements`, `Notifications`, `Theme` — HUD modules, hidden from the array list by design |

**World / Exploit / Misc**

| Module | Category | State | Description |
| :--- | :--- | :--: | :--- |
| AutoTools | World | ✅ | Auto-switch tools (Fastest / Durability priority, silent swap) |
| Disabler | Exploit | 🚧 | Bypass server restrictions |
| AimAssist | Misc | 🚧 | Smooth aim assist |

> 20 modules are registered in total; 4 of them are HUD infrastructure (always available but not listed in the array list).

---

## 🛠 Tech stack

| Item | Value |
| :--- | :--- |
| Minecraft | `1.20.1` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.92.11+1.20.1` |
| Java (runtime) | `17`+ (what MC 1.20.1 runs on) |
| JDK (development) | `21`+ — Fabric Loom 1.17 requires JDK 21; the CI workflow builds on JDK 25 |
| Build tooling | Gradle `9.5.1` (wrapper) + Fabric Loom `1.17-SNAPSHOT` |
| Mappings | Mojang Official Mappings (Mojmap) |
| Version | `beta1.0` |
| License | CC0-1.0 |

---

## 🚀 Getting started

### Option 1 — Install a prebuilt jar

1. Install the [Fabric Loader 0.19.3](https://fabricmc.net/use/installer/) for game version **1.20.1**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api).
3. Put `fabric-api-*.jar` and `cookie-client-beta1.0.jar` into `.minecraft/mods/`.
4. Launch the game. Press **Right Shift** to open the ClickGUI (unless a vanilla screen is open).

### Option 2 — Build from source

```bash
git clone https://github.com/xxliam/cookie-client.git
cd cookie-client

# Windows
./gradlew.bat build
# Linux / macOS
./gradlew build
```

Set `JAVA_HOME` to a **JDK 21+** first. Artifacts land in `build/libs/`:

- `cookie-client-beta1.0.jar` — the usable mod jar
- `cookie-client-beta1.0-sources.jar` — sources jar

### Run the dev client

```bash
./gradlew runClient
```

The dev environment (game dir + configs) lives in `run/` (git-ignored). Client configs are written to `<game-dir>/cookie-client/`:

| File | Content |
| :--- | :--- |
| `modules.json` | Per-module enable/disable state |
| `values.json` | Per-module setting values |
| `binds.json` | Per-module key binds |

### Default keys

| Key | Action |
| :--- | :--- |
| `Right Shift` | Open / close ClickGUI |
| `R` | Toggle KillAura (default bind) |

Every module bind can be changed with `.bind <module> <key>` or from the ClickGUI bind element (keyboard keys and `mouse4`–`mouse8` are accepted).

---

## 📁 Project structure

```
cookie-client/
├── src/main/
│   ├── java/xxliam/cookieclient/            # Main sources (Java)
│   │   ├── CookieClient.java                # Mod entry point (ModInitializer)
│   │   ├── command/                         # Command framework + impl (.toggle/.bind/.config)
│   │   ├── config/                          # modules.json / values.json / binds.json
│   │   ├── event/                           # Event bus (EventBus + events)
│   │   ├── exception/                       # Custom exceptions
│   │   ├── gui/
│   │   │   ├── NewClickGui.java             # ClickGUI screen + collapse E button
│   │   │   ├── newclickgui/                 # Category panels & setting elements
│   │   │   └── mainmenu/                    # Setsuna-style main menu + Alt Manager
│   │   ├── manager/                         # Module / Command / Config / Target / Notification managers
│   │   ├── mixin/                           # Mixin hooks (tick, keys, HUD, projection, title screen…)
│   │   ├── modules/                         # Feature modules
│   │   │   └── impl/                        # By category: combat·exploit·misc·movement·player·render·world
│   │   │       ├── render/hud/              # HUD modules (ModuleList / ClientElements / Notifications)
│   │   │       └── render/esp/              # Opal ESP renderer
│   │   ├── notification/                    # Notification queue (bottom-right toasts)
│   │   ├── render/                          # Renderer / CustomFont / FontStore / GlyphPage / shaders
│   │   ├── settings/                        # Setting system (Boolean/Number/Mode/MultiSelect/Color/Visibility)
│   │   └── utils/                           # animation / game / math / misc / render / rotation
│   └── resources/
│       ├── fabric.mod.json                  # Mod metadata
│       ├── cookie-client.mixins.json        # Mixin registration
│       └── assets/cookie-client/            # fonts/ + textures/mainmenu/
├── build.gradle.kts                         # Build script (Fabric Loom)
├── gradle.properties                        # Versions & mod properties
└── settings.gradle.kts                      # Project name (matches mod id)
```

---

## 🧑‍💻 Development guide

### Adding a new module

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
        // (name, category) or (name, category, defaultKeyBind)
        super("MyModule", Category.COMBAT, GLFW.GLFW_KEY_R);
        addSetting(range);
        addSetting(silent);
    }

    @Override
    protected void onEnable() { /* module enabled */ }

    @Override
    protected void onDisable() { /* module disabled */ }

    @Override
    public void onTick() { /* called every client tick while enabled */ }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) { /* HUD-style rendering */ }
}
```

Then register it in `ModuleManager` (`CookieClient.MODULE_MANAGER.add(new MyModule())`). Settings with a `() -> false` visibility are hidden from the ClickGUI (used by HUD infrastructure); `ModeSetting` falls back to its first mode if a saved value is unknown.

### Useful Gradle commands

| Command | Purpose |
| :--- | :--- |
| `./gradlew build` | Full build + jars |
| `./gradlew runClient` | Launch the dev client |
| `./gradlew compileJava` | Compile only |
| `./gradlew clean` | Clean build outputs |

> Building requires **JDK 21+** (Loom 1.17). If a build fails with `fileHashes.lock` "access denied" left by a previous daemon, run `./gradlew --stop` and retry.

---

## 🙏 Credits

- **OpenZen** — overall module/manager architecture this project is modeled on.
- **OpenOpal** — HUD elements, ESP, notifications and theme; rendering parameters (colors, fonts, sizes, spacing, easing) are reproduced faithfully from the original source.
- **Setsuna** — main menu & alt manager visual design and animation behavior.

---

## ⚠️ Disclaimer

This project is for **educational and research purposes only**. Client-side mods may violate the rules of some servers — **only use it where the server administration allows it**. Any ban or other consequence caused by using this client is the responsibility of the user, not the author.

---

## 📄 License

Released under the [CC0-1.0](./LICENSE) license (public domain dedication).

---

## 👤 Author

**xxliam**

- GitHub: [@xxliam](https://github.com/xxliam)
- Repository: [github.com/xxliam/cookie-client](https://github.com/xxliam/cookie-client)
- Issues: [report bugs & suggest features](https://github.com/xxliam/cookie-client/issues)

Issues and pull requests are welcome — let's keep baking Cookie Client 🍪
