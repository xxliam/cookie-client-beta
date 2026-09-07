package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;

/**
 * 布尔设置控件：开关样式切换条。
 */
public class BooleanSettingElement extends SettingElement<BooleanSetting> {

    private static final String ELLIPSIS = "...";

    private boolean isTruncated;
    private boolean isHovered;
    private final SmoothAnimationTimer toggleTimer = new SmoothAnimationTimer();

    public BooleanSettingElement(CategoryPanel parentPanel, BooleanSetting setting) {
        super(parentPanel, setting);
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        isHovered = CursorUtil.isInBounds(mouseX, mouseY, x, y, 120.0f, getHeight());
        visibilityTimer.animate(setting.getVisibility().displayable() ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        visibilityTimer.tick();
        alpha *= visibilityTimer.getValueF();
        if (Mth.equal(alpha, 0.0f)) {
            return;
        }
        String name = setting.getName();
        if (FontStore.AXIFORMA_REGULAR_14.getStringWidth(name) > 90.0f) {
            name = name.substring(0, Math.min(10, name.length())) + ELLIPSIS;
            isTruncated = true;
        }
        toggleTimer.animate(Boolean.TRUE.equals(setting.getValue()) ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        toggleTimer.tick();
        poseStack.pushPose();
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + 6.0f,
                y + (getHeight() - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f, ColorUtil.withAlpha(-1, alpha * 0.8f));
        float toggleAmount = toggleTimer.getValueF();
        float toggleX = x + 120.0f - 20.0f - 6.0f;
        float toggleY = y + (getHeight() - 10.0f) / 2.0f;
        Renderer.drawRoundedRect(poseStack, toggleX, toggleY, 20.0f, 10.0f, 4.0f, ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha));
        if (toggleAmount > 0.0f) {
            Renderer.drawRoundedRect(poseStack, toggleX, toggleY, 10.0f + 10.0f * toggleAmount, 10.0f, 4.0f,
                    ColorUtil.withAlpha(CategoryPanel.ACCENT_COLOR, alpha * toggleAmount));
        }
        Renderer.drawRoundedRect(poseStack, toggleX + 10.0f * toggleAmount, toggleY, 10.0f, 10.0f, 4.8f, ColorUtil.withAlpha(-1, alpha));
        poseStack.popPose();
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
    public float getHeight() {
        return 18.0f;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.getVisibility().displayable()) {
            return false;
        }
        if (isHovered && CursorUtil.isInBounds((float) mouseX, (float) mouseY, x, y, 120.0f, getHeight())) {
            setting.setValue(!Boolean.TRUE.equals(setting.getValue()));
            return true;
        }
        return false;
    }
}
