package xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.PropertyPanel;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;

/**
 * 数值滑杆行（Opal {@code NumberPropertyComponent}）：行高 26；滑轨 2.5px 圆角、
 * 拖动填充 = 主题主色→暗 0.5 水平渐变（angle 0）、滑块白→暗 0.1 垂直渐变；
 * 填充宽度动画 LINEAR 50ms；值随拖动实时写入，文本 medium 5.5 跟随滑杆居中显示。
 */
public class NumberSettingPanel extends PropertyPanel {

    private final NumberSetting setting;

    private boolean dragging;
    private SmoothAnimationTimer dragAnim;

    public NumberSettingPanel(final NumberSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        setHeight(26.0f);
        super.render(guiGraphics, mouseX, mouseY, delta, alpha);

        label(guiGraphics, setting.getName(), x + 5.0f, y + 8.5f, alpha);

        float sliderWidth = width - 12.0f;
        float sliderHeight = 2.5f;
        float sliderX = x + 6.0f;
        float sliderY = y + 13.0f;

        if (dragging && mouseX != -1) {
            float percent = Math.min(1.0f, Math.max(0.0f, (mouseX - sliderX) / sliderWidth));
            double value = interpolate(setting.getMin().doubleValue(), setting.getMax().doubleValue(), percent);
            setting.setValue(stepRound(value));
        }

        double widthPercent = (setting.getValue().doubleValue() - setting.getMin().doubleValue())
                / (setting.getMax().doubleValue() - setting.getMin().doubleValue());
        float destination = (float) (sliderWidth * widthPercent);

        if (dragAnim == null) {
            dragAnim = new SmoothAnimationTimer();
            dragAnim.setCurrentValue(destination);
        } else {
            dragAnim.animate(destination, 0.05, Easings.LINEAR);
            dragAnim.tick();
        }

        Renderer.drawRoundedRect(guiGraphics.pose(), sliderX, sliderY, sliderWidth, sliderHeight,
                sliderHeight / 2.0f, ColorUtil.withAlpha(0xFF373737, alpha));

        float drag = dragAnim.getValueF();
        if (drag > 1.0f) {
            int themeFirst = ThemeHelper.getThemeColors()[0];
            Renderer.drawRoundedRectGradient(guiGraphics.pose(), sliderX, sliderY, drag, sliderHeight,
                    sliderHeight / 2.0f,
                    ColorUtil.withAlpha(themeFirst, alpha),
                    ColorUtil.withAlpha(ColorUtil.darker(themeFirst, 0.5f), alpha), 90.0f);
        }
        // 滑块：白 → 暗 0.1 垂直渐变圆点
        Renderer.drawRoundedRectGradient(guiGraphics.pose(), sliderX + drag - 1.0f, sliderY - 1.3f, 2.0f, 5.0f, 1.0f,
                ColorUtil.withAlpha(-1, alpha), ColorUtil.withAlpha(ColorUtil.darker(-1, 0.1f), alpha), 90.0f);

        double value = setting.getValue().doubleValue();
        String valueString = format(value);
        CustomFont font = FontStore.PRODUCTSANS_MEDIUM_5_5;
        float valueWidth = font.getStringWidth(valueString);
        float valueX = Math.max(x + 4.0f, Math.min(x + width - valueWidth - 4.0f, sliderX + drag - valueWidth / 2.0f));
        baseline(guiGraphics, font, valueString, valueX, y + 22.0f, ColorUtil.applyOpacity(-1, 0.8f * alpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(x, y, width, height, mouseX, mouseY)) {
            dragging = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
        }
    }

    private double interpolate(double min, double max, double percent) {
        return min + (max - min) * percent;
    }

    /** 拖动写入按 step 取整（opal 无 step；cookie NumberSetting 带步进，逐格对齐更精确）。 */
    private double stepRound(double value) {
        double step = setting.getStep().doubleValue();
        if (step <= 0.0) {
            return value;
        }
        double snapped = Math.round((value - setting.getMin().doubleValue()) / step) * step + setting.getMin().doubleValue();
        return Math.min(setting.getMax().doubleValue(), Math.max(setting.getMin().doubleValue(), snapped));
    }

    private String format(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        String s = String.format("%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }
}
