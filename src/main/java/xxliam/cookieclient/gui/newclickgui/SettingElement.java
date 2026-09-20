package xxliam.cookieclient.gui.newclickgui;

import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;

/**
 * 设置项控件基类，绑定一个 {@link Setting} 与其所属面板。
 * <p>
 * 只提供几何 / 数据访问与可见性查询；鼠标键盘事件与拖动状态由
 * {@code gui.newclickgui.input.GuiInputRouter} 处理。
 */
public abstract class SettingElement<T extends Setting<?>> extends UIElement {

    protected final CategoryPanel parentPanel;
    protected final T setting;
    protected final SmoothAnimationTimer visibilityTimer = new SmoothAnimationTimer();

    public SettingElement(CategoryPanel parentPanel, T setting) {
        this.parentPanel = parentPanel;
        this.setting = setting;
    }

    public CategoryPanel getParentPanel() {
        return parentPanel;
    }

    public T getSetting() {
        return setting;
    }

    /** 该设置项当前是否可显示（可见性条件成立）。 */
    public boolean isDisplayable() {
        return setting.getVisibility().displayable();
    }

    public SmoothAnimationTimer getVisibilityTimer() {
        return visibilityTimer;
    }
}
