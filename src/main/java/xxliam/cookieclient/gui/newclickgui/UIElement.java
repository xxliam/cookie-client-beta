package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;

/**
 * ClickGUI 渲染元素基类（面板 / 模块 / 设置控件）。
 * <p>
 * <b>只负责渲染与几何查询</b>：位置 / 尺寸的读写、命中区域的纯函数（{@code contains*} 系列，
 * 由各子类按自身布局给出）、以及绘制。鼠标 / 键盘事件与拖动状态一律由
 * {@code gui.newclickgui.input.GuiInputRouter} 处理，元素只暴露语义化动作方法
 * （{@code toggle()} / {@code applySlider()} …）供其调用。
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

    /** 命中查询：元素包围框内（子类可用更精确的几何覆盖）。 */
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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
