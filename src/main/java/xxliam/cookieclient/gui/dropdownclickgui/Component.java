package xxliam.cookieclient.gui.dropdownclickgui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Dropdown GUI 组件基类：Opal ClickGUI 组件树（Screen → CategoryPanel → ModulePanel →
 * PropertyProvider → PropertyPanel）共用的坐标 / 生命周期 / 事件接口。
 * <p>
 * 对应 OpenOpal 的 {@code wtf.opal.client.screen.click.IOpalComponent} +
 * {@code OpalPanelComponent}（后者只是继承了含 x/y/width/height 的 ScreenPosition）。
 * cookie 侧不引入独立的 ScreenPosition 类型，坐标字段直接收进基类。
 * <p>
 * 额外引入 {@code alpha}：opal 用 {@code NVGRenderer.globalAlpha} 表达外层折叠透明度，
 * cookie 无全局 alpha，逐层把外层 alpha 乘进绘制色——统一在 render 签名传透。
 */
public abstract class Component {

    protected float x;
    protected float y;
    protected float width;
    protected float height;

    /** 每次 GUI 打开时调用（重置动画、构建子元素）。 */
    public void init() {
    }

    /** GUI 开始关闭时调用（触发收起动画）。 */
    public void close() {
    }

    public abstract void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha);

    public void mouseClicked(double mouseX, double mouseY, int button) {
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
    }

    public void keyPressed(int keyCode) {
    }

    public void charTyped(char chr, int modifiers) {
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setHeight(float height) {
        this.height = height;
    }
}
