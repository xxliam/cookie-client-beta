package xxliam.cookieclient.gui.newclickgui.input;

import xxliam.cookieclient.gui.newclickgui.NumberSettingElement;

/**
 * 数值滑块的拖动态管理（原散落在 {@code NumberSettingElement} 的 {@code isDragging} 字段）。
 * <p>
 * 全局同一时刻只可能有一个滑块在被拖动：按下时 {@link #begin} 记录，拖动 / 按下当帧
 * {@link #drag} 按鼠标位置回写数值，松开 {@link #release} 清空。
 */
public final class SliderInputHandler {

    private NumberSettingElement active;

    /** 滑块是否被按下（命中滑条）→ 进入拖动态并按首帧鼠标位置立即写入数值。 */
    boolean begin(NumberSettingElement element, double mouseX) {
        active = element;
        element.applySlider(mouseX);
        return true;
    }

    /** 拖动中：按当前鼠标位置回写数值。 */
    public boolean drag(double mouseX) {
        if (active == null) {
            return false;
        }
        active.applySlider(mouseX);
        return true;
    }

    /** 松开：结束拖动态。 */
    public boolean release() {
        if (active == null) {
            return false;
        }
        active = null;
        return true;
    }

    public boolean isDragging() {
        return active != null;
    }
}
