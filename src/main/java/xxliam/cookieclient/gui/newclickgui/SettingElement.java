package xxliam.cookieclient.gui.newclickgui;

import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;

/**
 * 设置项控件基类，绑定一个 {@link Setting} 与其所属面板。
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

    public SmoothAnimationTimer getVisibilityTimer() {
        return visibilityTimer;
    }
}
