package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;

/**
 * 按键绑定控件：展开模块底部的一行「Bind」按钮。
 * <p>
 * 点击进入监听态（右侧显示 {@code ...}），此时键入任意按键即可绑定；
 * ESC / Delete / Backspace 解绑（清空当前绑定，置为 None）。
 * 绑定结果写入 {@link Module#setKeyBind(int)}，右侧持续显示当前绑定的按键名。
 */
public class BindElement extends UIElement {

    /** 当前处于监听态的 BindElement（全局唯一），由 NewClickGui 的 keyPressed 路由按键。 */
    private static BindElement listening;

    private final CategoryPanel parentPanel;
    private final Module module;
    private boolean isListening;

    public BindElement(CategoryPanel parentPanel, Module module) {
        this.parentPanel = parentPanel;
        this.module = module;
    }

    public static BindElement getListening() {
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
    public float getHeight() {
        return 18.0f;
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float nameY = y + (getHeight() - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f;
        // "Bind" 标签靠左对齐（与其它设置项一致）
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, "Bind", x + 6.0f, nameY, ColorUtil.withAlpha(-1, alpha * 0.8f));

        // 右侧显示当前绑定的按键名（监听态显示 "..."，未绑定显示 "None"）
        String display;
        int color;
        if (isListening) {
            display = "...";
            color = CategoryPanel.ACCENT_COLOR;
        } else if (module.getKeyBind() <= 0) {
            display = "None";
            color = ColorUtil.withAlpha(-1, 0.5f);
        } else {
            display = getKeyName(module.getKeyBind());
            color = -1;
        }
        float displayX = x + 120.0f - FontStore.AXIFORMA_BOLD_13.getStringWidth(display) - 6.0f;
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, display, displayX, nameY, ColorUtil.withAlpha(color, alpha));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, x, y, 120.0f, getHeight())) {
            setListening(!isListening);
            return true;
        }
        return false;
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
        BindElement.listening = listening ? this : null;
    }

    public Module getModule() {
        return module;
    }

    public boolean isListening() {
        return isListening;
    }

    /** 将 GLFW 键码转换为可读的按键名（如 {@code R}、{@code Space}、{@code Mouse 4}）。 */
    public static String getKeyName(int key) {
        // 额外鼠标键：GLFW 码 3~7 对应物理 Mouse 4~8
        if (isBindableMouseButton(key)) {
            return "Mouse " + (key - GLFW.GLFW_MOUSE_BUTTON_1 + 1);
        }
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }
}
