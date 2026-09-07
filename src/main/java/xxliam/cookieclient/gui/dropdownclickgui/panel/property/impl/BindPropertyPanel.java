package xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl;

import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownRender;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.PropertyPanel;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;

/**
 * 按键绑定行（Zen {@code BindElement} 的 dropdown 移植）：展开区底部一行「Bind」，
 * 左侧名称右侧显示当前绑定键名；左键点击进入 / 退出监听态。
 * <p>
 * 交互规则与 zen 完全一致：点击进入监听后，任意键盘键绑定；ESC / Delete / Backspace
 * 解绑（清空为 None）；鼠标侧键（GLFW 4~8，码 3~7）可绑定为鼠标键；其余鼠标键不消费，
 * 由 Screen 原样下发（监听不被打断）。监听态全局唯一，见 {@link #getListening()}。
 */
public class BindPropertyPanel extends PropertyPanel {

    /** 当前处于监听态的 BindPropertyPanel（全局唯一），由 DropdownClickGui 路由按键 / 鼠标。 */
    private static BindPropertyPanel listening;

    private final Module module;
    private boolean isListening;

    public BindPropertyPanel(final Module module) {
        super(null);
        this.module = module;
        setHeight(DEFAULT_HEIGHT);
    }

    public static BindPropertyPanel getListening() {
        return listening;
    }

    /** 清除监听态（GUI 关闭时调用，避免残留引用）。 */
    public static void clearListening() {
        if (listening != null) {
            listening.isListening = false;
            listening = null;
        }
    }

    @Override
    public void init() {
        isListening = false;
        if (listening == this) {
            listening = null;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        super.render(guiGraphics, mouseX, mouseY, delta, alpha);

        CustomFont font = FontStore.PRODUCTSANS_MEDIUM_7;
        DropdownRender.baseline(guiGraphics, font, "Bind", x + 5.0f, y + 10.5f, ColorUtil.withAlpha(-1, alpha));

        // 右侧绑定键名（监听态 "..."，未绑定 "None"）
        String display;
        int color;
        if (isListening) {
            display = "...";
            color = ThemeHelper.getThemeColors()[0];
        } else if (module.getKeyBind() <= 0) {
            display = "None";
            color = ColorUtil.applyOpacity(-1, 0.5f);
        } else {
            display = DropdownRender.keyName(module.getKeyBind());
            color = -1;
        }
        CustomFont keyFont = FontStore.PRODUCTSANS_BOLD_7;
        float keyWidth = keyFont.getStringWidth(display);
        DropdownRender.baseline(guiGraphics, keyFont, display,
                x + width - keyWidth - 5.0f, y + 10.5f, ColorUtil.withAlpha(color, alpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (isHovering(x, y, width, getHeight(), mouseX, mouseY)) {
            setListening(!isListening);
        }
    }

    /**
     * 监听态下处理按键。返回 true 表示已消费该按键。
     *
     * @param key GLFW 键码
     */
    public boolean onKey(int key) {
        // ESC / Delete / Backspace 解绑（清空当前按键绑定）
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            module.setKeyBind(0);
            setListening(false);
            return true;
        }
        module.setKeyBind(key);
        setListening(false);
        return true;
    }

    /**
     * 监听态下处理鼠标键。仅认可额外鼠标键（侧键 4~8，GLFW 按钮码 3~7）；
     * 左键(0)与"未绑定"哨兵冲突，中/右键保留给原版交互，均不可绑定。
     *
     * @param button GLFW 鼠标按钮码
     */
    public boolean onMouse(int button) {
        if (!isBindableMouseButton(button)) {
            return false;
        }
        module.setKeyBind(button);
        setListening(false);
        return true;
    }

    /** 该 GLFW 鼠标按钮码是否可被绑定（GLFW_MOUSE_BUTTON_4=3 .. GLFW_MOUSE_BUTTON_LAST=7）。 */
    public static boolean isBindableMouseButton(int button) {
        return button >= GLFW.GLFW_MOUSE_BUTTON_4 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST;
    }

    private void setListening(boolean listening) {
        this.isListening = listening;
        BindPropertyPanel.listening = listening ? this : null;
    }

    public Module getModule() {
        return module;
    }

    public boolean isListening() {
        return isListening;
    }
}