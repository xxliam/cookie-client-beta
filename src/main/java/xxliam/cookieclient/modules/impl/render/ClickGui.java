package xxliam.cookieclient.modules.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.NewClickGui;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownClickGui;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;

/**
 * ClickGUI 总开关：绑定键（默认右 Shift）打开 / 关闭设置界面。
 * <p>
 * Style 二选一：{@code Opal} = Opal DropdownClickGui（顶部横向分类条 + 下拉模块树），
 * {@code Zen} = 原生 NewClickGui（分类面板横排）。两者共用一个入口键，由本模块路由。
 * <p>
 * 生命周期与 GUI 绑定：onEnable 打开对应风格 Screen，Screen 播放完关闭动画后回调
 * {@link #onGuiClosed()} 将本模块复位——因此本模块的 enabled 状态只在 GUI 打开期间为
 * true，配置持久化的是关闭态，重进游戏不会自动弹窗。
 * <p>
 * setVisible(false)：不进 ModuleList HUD 阵列（避免常显一行闪烁的开关）；同时模块开关
 * 通知（{@code Notifications.onModuleToggled}）只看 isVisible()，自然静音。
 */
public class ClickGui extends Module {

    public static final String STYLE_OPAL = "Opal";
    public static final String STYLE_ZEN = "Zen";

    public static ClickGui INSTANCE;

    private final ModeSetting style;
    private final BooleanSetting allowDrag;

    public ClickGui() {
        super("ClickGui", Category.RENDER, GLFW.GLFW_KEY_RIGHT_SHIFT);
        setVisible(false);
        style = new ModeSetting("Style", STYLE_OPAL, STYLE_ZEN).withDefault(STYLE_OPAL);
        allowDrag = new BooleanSetting("Allow drag", true);
        addSetting(style);
        addSetting(allowDrag);
        INSTANCE = this;
    }

    /** GUI 模块的 enabled 只在界面打开期间有意义，绝不写入 modules.json / 启动恢复。 */
    @Override
    public boolean shouldPersistEnabled() {
        return false;
    }

    /** 当前 GUI 风格（供路由 / 其它模块查询）。 */
    public static String getStyle() {
        return INSTANCE != null ? INSTANCE.style.getValue() : STYLE_OPAL;
    }

    /** Dropdown 是否允许拖动分类条（仅 Opal 风格读取）。 */
    public static boolean isAllowDrag() {
        return INSTANCE == null || INSTANCE.allowDrag.getValue();
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
        if (STYLE_ZEN.equals(style.getValue())) {
            mc.setScreen(new NewClickGui());
        } else {
            mc.setScreen(new DropdownClickGui());
        }
        CookieClient.LOGGER.info("[ClickGui] enabled, opened " + (STYLE_ZEN.equals(style.getValue()) ? "NewClickGui" : "DropdownClickGui"));
    }

    @Override
    protected void onDisable() {
        // 模块在 GUI 内部被关 / 快捷键关：同步关闭当前打开的对应风格 Screen（走其动画）
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Screen screen = mc.screen;
        if (screen instanceof NewClickGui || screen instanceof DropdownClickGui) {
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
