package xxliam.cookieclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.newclickgui.BindElement;
import xxliam.cookieclient.gui.newclickgui.CategoryPanel;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.RenderHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI：按分类横排的面板，含打开 / 关闭缩放动画。
 * <p>
 * 搬运自 OpenZen 的 {@code shit.zen.gui.NewClickGui}，额外支持整体等比缩放
 * （{@link #GUI_SCALE} 作用于整个 GUI：渲染变换 + 鼠标坐标反算 + scissor 换算）。
 */
public class NewClickGui extends Screen {

    private static final List<CategoryPanel> CATEGORY_PANELS = new ArrayList<>();
    public static CategoryPanel focusedPanel;

    /** ClickGUI 整体等比缩放系数（0.8 = 缩小 20%）。 */
    public static final float GUI_SCALE = 0.8f;
    /** 缩放垂直锚点：面板标题栏所在 y（面板从该行向下生长，缩放后顶部仍贴原高度）。 */
    public static final float GUI_ANCHOR_Y = 36.0f;

    private boolean closing;
    private final SmoothAnimationTimer closeAnim = new SmoothAnimationTimer();

    public NewClickGui() {
        super(Component.literal("Cookie Client ClickGUI"));
    }

    @Override
    protected void init() {
        focusedPanel = CATEGORY_PANELS.get(0);
        // 7 个面板，每个宽 120、间距 8，总宽 888，起点居中 = width/2 - 444
        float panelX = (float) this.width / 2.0f - 444.0f;
        for (CategoryPanel panel : CATEGORY_PANELS) {
            panel.setX(panelX);
            panel.setY(GUI_ANCHOR_Y);
            panelX += 128.0f;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        closeAnim.animate(closing ? 0.0 : 1.0, 0.2, Easings.EASE_OUT_POW2);
        closeAnim.tick();
        float closeProgress = closeAnim.getValueF();
        if (Mth.equal(closeProgress, 0.0f) && closing) {
            closing = false;
            super.onClose();
            for (CategoryPanel panel : CATEGORY_PANELS) {
                panel.reset();
            }
            CookieClient.CONFIG_MANAGER.save(); // 持久化本次 GUI 会话中的开关/设置/绑定
            return;
        }
        // 整体等比缩放：水平以屏幕中心为轴、垂直以标题栏行为轴；命中检测须反算回未缩放空间
        float anchorX = anchorX();
        RenderHelper.pushScaleAround(guiGraphics.pose(), anchorX, GUI_ANCHOR_Y, GUI_SCALE);
        int localMouseX = (int) toLocalX(mouseX);
        int localMouseY = (int) toLocalY(mouseY);
        for (CategoryPanel panel : CATEGORY_PANELS) {
            panel.render(this, guiGraphics, guiGraphics.pose(), localMouseX, localMouseY, closeProgress, delta);
        }
        RenderHelper.popPose(guiGraphics.pose());
    }

    @Override
    public void onClose() {
        closing = true;
        BindElement.clearListening();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 存在监听态的 Bind 按钮时，优先把按键交给它绑定
        BindElement listening = BindElement.getListening();
        if (listening != null) {
            return listening.onKey(keyCode);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 存在监听态的 Bind 按钮时，侧键按下直接作为鼠标绑定捕获（与 keyPressed 路由按键对称）
        BindElement listening = BindElement.getListening();
        if (listening != null && listening.onMouse(button)) {
            return true;
        }
        for (CategoryPanel panel : CATEGORY_PANELS) {
            if (panel.mouseClicked(toLocalX(mouseX), toLocalY(mouseY), button)) {
                focusedPanel = panel;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (CategoryPanel panel : CATEGORY_PANELS) {
            panel.mouseReleased(toLocalX(mouseX), toLocalY(mouseY), button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        for (CategoryPanel panel : CATEGORY_PANELS) {
            if (panel.mouseScrolled(toLocalX(mouseX), toLocalY(mouseY), scrollDelta)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean isClosing() {
        return closing;
    }

    // ---------------------------------------------------------------------
    // 全局缩放坐标映射（GUI_SCALE 作用于整个 ClickGUI）
    // 锚点：水平 = 屏幕水平中心；垂直 = 面板标题栏行 GUI_ANCHOR_Y
    // ---------------------------------------------------------------------

    /** 水平缩放锚点：屏幕水平中心。 */
    public float anchorX() {
        return this.width / 2.0f;
    }

    /** 屏幕 x → 未缩放空间 x（鼠标命中反算）。 */
    public double toLocalX(double screenX) {
        float anchorX = anchorX();
        return (screenX - anchorX) / GUI_SCALE + anchorX;
    }

    /** 屏幕 y → 未缩放空间 y（鼠标命中反算）。 */
    public double toLocalY(double screenY) {
        return (screenY - GUI_ANCHOR_Y) / GUI_SCALE + GUI_ANCHOR_Y;
    }

    /** 面板局部 x → 缩放后屏幕 x（scissor 等不受 Pose 影响的绘制需要）。 */
    public float toScaledX(float localX) {
        float anchorX = anchorX();
        return anchorX + (localX - anchorX) * GUI_SCALE;
    }

    /** 面板局部 y → 缩放后屏幕 y。 */
    public float toScaledY(float localY) {
        return GUI_ANCHOR_Y + (localY - GUI_ANCHOR_Y) * GUI_SCALE;
    }

    static {
        for (Category category : Category.values()) {
            CATEGORY_PANELS.add(new CategoryPanel(category));
        }
    }
}
