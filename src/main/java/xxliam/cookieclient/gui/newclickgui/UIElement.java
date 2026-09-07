package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;

/**
 * ClickGUI 可交互元素基类（面板 / 模块 / 设置控件）。
 */
public abstract class UIElement {

    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected final SmoothAnimationTimer animTimer = new SmoothAnimationTimer();

    public abstract void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack,
                                int mouseX, int mouseY, float alpha, float partialTicks);

    public void reset() {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return false;
    }

    public float getAnimatedHeight() {
        return getHeight();
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public SmoothAnimationTimer getAnimTimer() {
        return animTimer;
    }
}
