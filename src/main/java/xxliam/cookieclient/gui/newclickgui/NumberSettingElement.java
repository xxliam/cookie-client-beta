package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.gui.NewClickGui;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.math.MathUtil;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;

/**
 * 数值设置控件：滑块（可拖拽）。
 */
public class NumberSettingElement extends SettingElement<NumberSetting> {

    private final SmoothAnimationTimer sliderTimer = new SmoothAnimationTimer();
    private boolean isTruncated;
    private boolean isHovered;
    private boolean isDragging;

    public NumberSettingElement(CategoryPanel parentPanel, NumberSetting setting) {
        super(parentPanel, setting);
    }

    @Override
    public float getHeight() {
        return 30.0f;
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        isHovered = CursorUtil.isInBounds(mouseX, mouseY, x, y, 120.0f, getHeight());
        float sliderWidth = 108.0f;
        float sliderHeight = 5.0f;
        float sliderY = y + getHeight() / 2.0f + (getHeight() / 2.0f - sliderHeight) / 2.0f;
        visibilityTimer.animate(setting.getVisibility().displayable() ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        visibilityTimer.tick();
        alpha *= visibilityTimer.getValueF();
        if (Mth.equal(alpha, 0.0f)) {
            return;
        }
        float nameY = y + (getHeight() / 2.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        String name = setting.getName();
        if (FontStore.AXIFORMA_REGULAR_14.getStringWidth(name) > 78.0f) {
            name = name.substring(0, Math.min(10, name.length())) + "...";
            isTruncated = true;
        }
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + 6.0f, nameY, ColorUtil.withAlpha(-1, alpha * 0.8f));
        String valueText = String.format("%.2f", setting.getValue().floatValue());
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, valueText,
                x + 120.0f - FontStore.AXIFORMA_BOLD_13.getStringWidth(valueText) - 6.0f, nameY, ColorUtil.withAlpha(-1, alpha * 0.92f));
        // 归一化进度必须带 min：value 可能为负 / min 可能 <0，
        // value/max 会让负值或 0 全部钳到最左端（圆点卡死）。
        float settingMin = setting.getMin().floatValue();
        float settingSpan = setting.getMax().floatValue() - settingMin;
        float progress = Mth.clamp((setting.getValue().floatValue() - settingMin) / settingSpan, 0.0f, 1.0f);
        sliderTimer.animate(progress, 0.2, Easings.EASE_OUT_POW2);
        sliderTimer.tick();
        if (isDragging) {
            float dragRatio = ((float) mouseX - (x + 6.0f)) / sliderWidth;
            double rawValue = setting.getMin().floatValue() + (setting.getMax().floatValue() - setting.getMin().floatValue()) * dragRatio;
            double step = setting.getStep().floatValue();
            double stepped = Math.round(MathUtil.clamp(rawValue, setting.getMin().floatValue(), setting.getMax().floatValue()) / step) * step;
            setting.setValue(Math.round(stepped * 1000.0) / 1000.0);
        }
        Renderer.drawRoundedRect(poseStack, x + 6.0f, sliderY, sliderWidth, sliderHeight, 2.0f, ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha));
        float fillAmount = sliderTimer.getValueF();
        Renderer.drawRoundedRect(poseStack, x + 6.0f, sliderY, sliderWidth * fillAmount, sliderHeight, 2.0f, ColorUtil.withAlpha(CategoryPanel.ACCENT_COLOR, alpha));
        float knobX = Math.max(x + 6.0f + sliderTimer.getValueF() * sliderWidth - 5.0f - 0.5f, x + 6.0f - 0.5f);
        Renderer.drawRoundedRect(poseStack, knobX, sliderY - 0.5f, 6.0f, 6.0f, 2.9f, ColorUtil.withAlpha(-1, alpha));
        if (isHovered && isTruncated) {
            parentPanel.setHoveredSettingElement(this);
            parentPanel.setTooltipText(setting.getName());
            parentPanel.setShowTooltip(true);
        } else if (parentPanel.getHoveredSettingElement() == this) {
            parentPanel.setShowTooltip(false);
            parentPanel.setHoveredSettingElement(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.getVisibility().displayable()) {
            return false;
        }
        float sliderY = y + getHeight() / 2.0f + (getHeight() / 2.0f - 5.0f) / 2.0f;
        if (isHovered && CursorUtil.isInBounds((float) mouseX, (float) mouseY, x + 6.0f, sliderY, 108.0f, 5.0f)) {
            isDragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        return false;
    }
}
