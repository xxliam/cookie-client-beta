package xxliam.cookieclient.modules.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.newclickgui.NewClickGui;
import xxliam.cookieclient.gui.panelclickgui.PanelClickGui;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.ModeSetting;

/**
 * ClickGUI 总开关：绑定键（默认右 Shift）打开 / 关闭设置界面。
 * <p>
 * 界面风格由 {@code Mode} 设置切换（照搬 OpenZen 的 ClickGuiModule.Mode）：
 * <ul>
 *     <li>{@code Zen}（默认）→ {@link NewClickGui}：分类面板横排（zen 的 newclickgui）；</li>
 *     <li>{@code Panel} → {@link PanelClickGui}：屏幕中央 600×400 大面板（zen 的 PanelClickGui，
 *         含图标分类栏 / 模块列表 / 设置面板 / Profile 头像组件 / toast / 按键浮层）。</li>
 * </ul>
 * 历史上曾支持与 OpenOpal 的 DropdownClickGui 二选一（{@code Style} 设置），2026-09-18 按用户要求
 * **整体删除 opal 风格**，连带删掉两个只为它服务的设置（{@code Scale} 滑条、{@code Allow drag}）。
 * <p>
 * 生命周期与 GUI 绑定：onEnable 打开 Screen，Screen 播放完关闭动画后回调
 * {@link #onGuiClosed()} 将本模块复位——因此本模块的 enabled 状态只在 GUI 打开期间为
 * true，配置持久化的是关闭态，重进游戏不会自动弹窗。
 * <p>
 * setVisible(false)：不进 ModuleList HUD 阵列（避免常显一行闪烁的开关）；同时模块开关
 * 通知（{@code Notifications.onModuleToggled}）只看 isVisible()，自然静音。
 */
public class ClickGui extends Module {

    /**
     * GUI 尺寸系数（固定值）。
     * <p>
     * zen 的原始尺寸是硬编码常量 {@code GUI_SCALE = 0.80f}（缩小 20%）；本值 = 0.80 × 1.2 = 0.96，
     * 即「调回原本大小并加大 20%」。原 opal 风格与其绝对尺度滑条（{@code Scale}）已随 opal GUI 一并删除。
     * <p>
     * 仅作用于 {@link NewClickGui}；{@link PanelClickGui} 有自己的缩放档位（ProfileWidget 的
     * SettingsPopup 里 50%~150%），两者互不影响。
     */
    public static final float ZEN_SCALE = 0.96f;

    public static ClickGui INSTANCE;

    /** 界面风格：Zen（zen 的分类面板横排）| Panel（zen 的居中大面板）。默认 Zen。 */
    public final ModeSetting mode = new ModeSetting("Mode", "Zen", "Panel").withDefault("Zen");

    public ClickGui() {
        super("ClickGui", Category.RENDER, GLFW.GLFW_KEY_RIGHT_SHIFT);
        setVisible(false);
        addSetting(mode);
        INSTANCE = this;
    }

    /** GUI 尺寸系数：固定 {@link #ZEN_SCALE}（原 0.80 加大 20%）。 */
    public static float getZenScaleFactor() {
        return ZEN_SCALE;
    }

    /** GUI 模块的 enabled 只在界面打开期间有意义，绝不写入 modules.json / 启动恢复。 */
    @Override
    public boolean shouldPersistEnabled() {
        return false;
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        // 启动早期（mod 入口在 Minecraft.<init> 中执行）mouseHandler 尚未创建，
        // 此刻 setScreen 会 NPE；同时无 world 时也不需要弹 GUI。防御性复位避免
        // enabled 残留 + 启动崩溃（正常途径下本模块已排除配置持久化，不应走到这里）。
        if (mc == null || mc.mouseHandler == null || mc.screen != null || mc.player == null) {
            CookieClient.LOGGER.info("[ClickGui] cannot open now (game not ready / screen busy / no world), self-disable");
            disable();
            return;
        }
        if (mode.is("Panel")) {
            mc.setScreen(PanelClickGui.INSTANCE);
            CookieClient.LOGGER.info("[ClickGui] enabled, opened PanelClickGui");
        } else {
            mc.setScreen(new NewClickGui());
            CookieClient.LOGGER.info("[ClickGui] enabled, opened NewClickGui");
        }
    }

    @Override
    protected void onDisable() {
        // 模块在 GUI 内部被关 / 快捷键关：同步关闭当前打开的 Screen（走其动画）
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Screen screen = mc.screen;
        if (screen instanceof NewClickGui || screen instanceof PanelClickGui) {
            CookieClient.LOGGER.info("[ClickGui] disabled, closing screen " + screen.getClass().getSimpleName());
            screen.onClose();
        }
    }

    /** GUI 完全关闭（动画播完、Screen 已卸载）后回调：复位本模块，避免 enabled 残留。 */
    public static void onGuiClosed() {
        if (INSTANCE != null && INSTANCE.isEnabled()) {
            CookieClient.LOGGER.info("[ClickGui] gui fully closed -> disabling module");
            INSTANCE.disable();
        }
    }
}
