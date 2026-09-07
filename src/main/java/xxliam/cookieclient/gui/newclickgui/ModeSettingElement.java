package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;

/**
 * 枚举设置控件：点击展开下拉选择。
 */
public class ModeSettingElement extends SettingElement<ModeSetting> {

    private final SmoothAnimationTimer hoverTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer visTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer highlightTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer highlightYTimer = new SmoothAnimationTimer();
    private boolean isDropdownHovered;
    private boolean isOpen;
    private String hoveredMode;

    public ModeSettingElement(CategoryPanel parentPanel, ModeSetting setting) {
        super(parentPanel, setting);
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float totalHeight = 36.0f;
        float dropdownY = y + totalHeight / 2.0f + 2.0f - 2.0f;
        float dropdownWidth = 108.0f;
        float itemHeight = 14.0f;
        hoveredMode = null;
        isDropdownHovered = CursorUtil.isInBounds(mouseX, mouseY, x + 6.0f, dropdownY, dropdownWidth, itemHeight);
        hoverTimer.animate(isDropdownHovered ? 1.0 : 0.0, 0.22, Easings.EASE_OUT_POW2);
        hoverTimer.tick();
        visibilityTimer.animate(setting.getVisibility().displayable() ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        visibilityTimer.tick();
        visTimer.animate(isOpen ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        visTimer.tick();
        alpha *= visibilityTimer.getValueF();
        if (Mth.equal(alpha, 0.0f)) {
            return;
        }
        float nameY = y + (totalHeight / 2.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, setting.getName(), x + 6.0f, nameY, ColorUtil.withAlpha(-1, alpha * 0.8f));
        float openAmount = visTimer.getValueF();
        if (openAmount > 0.0f) {
            float dropdownHeight = itemHeight + setting.getModes().length * itemHeight * openAmount;
            Renderer.drawRoundedRect(poseStack, x + 6.0f, dropdownY, dropdownWidth, dropdownHeight, 3.0f,
                    ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha * openAmount));
            float hoverOffset = (itemHeight - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f;
            float itemY = dropdownY + hoverOffset + itemHeight;
            for (String mode : setting.getModes()) {
                if (CursorUtil.isInBounds(mouseX, mouseY, x + 6.0f, itemY - hoverOffset, dropdownWidth, itemHeight)) {
                    hoveredMode = mode;
                    highlightYTimer.animate(itemY - hoverOffset, 0.2, Easings.EASE_OUT_POW2);
                }
                if (dropdownY + dropdownHeight > itemY + FontStore.AXIFORMA_BOLD_13.getFontHeight()) {
                    FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, mode, x + 60.0f, itemY, ColorUtil.withAlpha(-1, alpha * 0.8f * openAmount));
                }
                itemY += itemHeight;
            }
        }
        highlightTimer.animate(hoveredMode == null || !isOpen ? 0.0 : 1.0, 0.18, Easings.EASE_OUT_POW2);
        highlightTimer.tick();
        highlightYTimer.tick();
        float highlightAmount = highlightTimer.getValueF();
        if (highlightAmount > 0.0f) {
            Renderer.drawRoundedRect(poseStack, x + 6.0f, highlightYTimer.getValueF(), dropdownWidth, itemHeight, 3.0f,
                    ColorUtil.withAlpha(ColorUtil.fromRGB(255, 255, 255), alpha * highlightAmount * 0.1f));
        }
        float hoverAmount = hoverTimer.getValueF();
        Renderer.drawRoundedRect(poseStack, x + 6.0f, dropdownY, dropdownWidth, itemHeight, 3.0f,
                ColorUtil.withAlpha(ColorUtil.fromRGB((int) (60.0f + 30.0f * hoverAmount), (int) (60.0f + 30.0f * hoverAmount), (int) (60.0f + 30.0f * hoverAmount)), alpha));
        String value = setting.getValue();
        if (value != null) {
            FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, value, x + 60.0f,
                    dropdownY + (itemHeight - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f, ColorUtil.withAlpha(-1, alpha * 0.8f));
        }
        String arrowIcon = String.valueOf('\ueb5d');
        FontStore.MATERIAL_20.drawStringCentered(poseStack, arrowIcon,
                x + 6.0f + dropdownWidth - FontStore.MATERIAL_20.getStringWidth(arrowIcon) + 2.0f,
                dropdownY + (itemHeight - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 0.5f, ColorUtil.withAlpha(-1, alpha * 0.8f));
    }

    @Override
    public float getHeight() {
        return 36 + (isOpen ? 14 * setting.getModes().length : 0);
    }

    @Override
    public float getAnimatedHeight() {
        return 36.0f + (14 * setting.getModes().length) * visTimer.getValueF();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.getVisibility().displayable()) {
            return false;
        }
        if (isDropdownHovered) {
            isOpen = !isOpen;
            return true;
        }
        if (hoveredMode != null && isOpen) {
            setting.setValue(hoveredMode);
            isOpen = false;
            hoveredMode = null;
            return true;
        }
        return false;
    }
}
