package xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.PropertyPanel;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;
import xxliam.cookieclient.utils.render.ThemeHelper;

/**
 * 布尔开关行（Opal {@code BooleanPropertyComponent}）：
 * 行内左侧名称，右侧 ToggleSwitch 开关（视觉参数照搬：宽 20×高 10、缩放 0.85、
 * 轨道色 = 0xff3c3c3c ↔ 主题主色 插值、旋钮白→暗 0.1、150ms DECELERATE 动画）。
 */
public class BooleanSettingPanel extends PropertyPanel {

    private final BooleanSetting setting;

    private SmoothAnimationTimer toggleAnim;

    public BooleanSettingPanel(final BooleanSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void init() {
        toggleAnim = null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        super.render(guiGraphics, mouseX, mouseY, delta, alpha);
        label(guiGraphics, setting.getName(), x + 5.0f, y + 10.5f, alpha);

        // 开关盒（opal ToggleSwitchComponent）：x+88 / y+3.8，缩放 0.85 绕盒中心
        float boxX = x + 88.0f;
        float boxY = y + 3.8f;
        float boxW = 20.0f;
        float boxH = 10.0f;
        float cx = boxX + boxW / 2.0f;
        float cy = boxY + boxH / 2.0f;

        if (toggleAnim == null) {
            toggleAnim = new SmoothAnimationTimer();
            toggleAnim.setCurrentValue(setting.getValue() ? 1.0 : 0.0);
        } else {
            toggleAnim.animate(setting.getValue() ? 1.0 : 0.0, 0.15, Easings.EASE_OUT_QUAD);
            toggleAnim.tick();
        }
        float anim = toggleAnim.getValueF();
        int themeFirst = ThemeHelper.getThemeColors()[0];
        // opal ToggleSwitchComponent.setBoxColors：关闭轨道 0xff3c3c3c → 开启主题主色
        int color1 = ColorUtil.interpolateColors(0xff3c3c3c, themeFirst, anim);
        int color2 = ColorUtil.darker(color1, 0.4f);

        RenderHelper.pushScaleAround(guiGraphics.pose(), cx, cy, 0.85f);
        Renderer.drawRoundedRectGradient(guiGraphics.pose(), boxX, boxY, boxW, boxH, boxH / 2.0f,
                ColorUtil.withAlpha(color1, alpha), ColorUtil.withAlpha(color2, alpha), 90.0f);
        float knob = boxX + 1.0f + anim * 9.5f;
        float knobSize = boxH - 2.0f;
        Renderer.drawRoundedRectGradient(guiGraphics.pose(), knob, boxY + 1.0f, knobSize, knobSize, knobSize / 2.0f,
                ColorUtil.withAlpha(-1, alpha), ColorUtil.withAlpha(ColorUtil.darker(-1, 0.1f), alpha), 90.0f);
        RenderHelper.popPose(guiGraphics.pose());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
            setting.setValue(!setting.getValue());
        }
    }
}
