package xxliam.cookieclient.gui.newclickgui.input;

import xxliam.cookieclient.gui.newclickgui.ColorSettingElement;

/**
 * HSV 色谱取色盘的拖动态管理（原散落在 {@code ColorSettingElement} 的 {@code dragType} 字段）。
 * <p>
 * 全局同一时刻只可能有一个取色盘被拖动：按下时记下拖动区域（拾色器 / 色相条）并按首帧
 * 鼠标位置立即写入，拖动中 {@link #drag} 持续回写，松开 {@link #release} 清空。
 */
public final class ColorPickerInputHandler {

    /** 取色盘内的拖动区域。 */
    public enum Zone {
        /** 拾色器（横 = 饱和度，纵 = 亮度）。 */
        PICKER,
        /** 竖向色相条。 */
        HUE
    }

    private ColorSettingElement active;
    private Zone zone;

    boolean begin(ColorSettingElement element, Zone zone, double mouseX, double mouseY) {
        this.active = element;
        this.zone = zone;
        apply(mouseX, mouseY);
        return true;
    }

    /** 拖动中：按当前鼠标位置回写 HSB。 */
    public boolean drag(double mouseX, double mouseY) {
        if (active == null) {
            return false;
        }
        apply(mouseX, mouseY);
        return true;
    }

    /** 松开：结束拖动态。 */
    public boolean release() {
        if (active == null) {
            return false;
        }
        active = null;
        zone = null;
        return true;
    }

    public boolean isDragging() {
        return active != null;
    }

    private void apply(double mouseX, double mouseY) {
        if (zone == Zone.HUE) {
            active.applyHue(mouseY);
        } else {
            active.applyPicker(mouseX, mouseY);
        }
    }
}
