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
import xxliam.cookieclient.utils.render.ThemeHelper;

/**
 * 枚举设置控件：点击展开下拉选择。
 * <p>
 * 纯视图：绘制 + 几何查询（{@link #dropdownContains} / {@link #itemAt}）+ 语义化动作
 * （{@link #toggleOpen()} / {@link #select(String)}）。展开态与 hover 高亮属于显示状态，留在本类。
 */
public class ModeSettingElement extends SettingElement<ModeSetting> {

    private static final float TOTAL_HEIGHT = 36.0f;
    private static final float DROPDOWN_WIDTH = 108.0f;
    private static final float ITEM_HEIGHT = 14.0f;

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

    /** 下拉首行（当前值那一行）的 y（与绘制共用同一公式）。 */
    private float dropdownY() {
        return y + TOTAL_HEIGHT / 2.0f + 2.0f - 2.0f;
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float dropdownY = dropdownY();
        hoveredMode = null;
        isDropdownHovered = CursorUtil.isInBounds(mouseX, mouseY, x + 6.0f, dropdownY, DROPDOWN_WIDTH, ITEM_HEIGHT);
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
        float nameY = y + (TOTAL_HEIGHT / 2.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, setting.getName(), x + 6.0f, nameY, ThemeHelper.foreground(alpha * 0.8f));
        float openAmount = visTimer.getValueF();
        if (openAmount > 0.0f) {
            float dropdownHeight = ITEM_HEIGHT + setting.getModes().length * ITEM_HEIGHT * openAmount;
            Renderer.drawRoundedRect(poseStack, x + 6.0f, dropdownY, DROPDOWN_WIDTH, dropdownHeight, 3.0f,
                    ThemeHelper.control(alpha * openAmount));
            float hoverOffset = (ITEM_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f;
            float itemY = dropdownY + hoverOffset + ITEM_HEIGHT;
            for (String mode : setting.getModes()) {
                if (CursorUtil.isInBounds(mouseX, mouseY, x + 6.0f, itemY - hoverOffset, DROPDOWN_WIDTH, ITEM_HEIGHT)) {
                    hoveredMode = mode;
                    highlightYTimer.animate(itemY - hoverOffset, 0.2, Easings.EASE_OUT_POW2);
                }
                if (dropdownY + dropdownHeight > itemY + FontStore.AXIFORMA_BOLD_13.getFontHeight()) {
                    FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, mode, x + 60.0f, itemY, ThemeHelper.foreground(alpha * 0.8f * openAmount));
                }
                itemY += ITEM_HEIGHT;
            }
        }
        highlightTimer.animate(hoveredMode == null || !isOpen ? 0.0 : 1.0, 0.18, Easings.EASE_OUT_POW2);
        highlightTimer.tick();
        highlightYTimer.tick();
        float highlightAmount = highlightTimer.getValueF();
        if (highlightAmount > 0.0f) {
            Renderer.drawRoundedRect(poseStack, x + 6.0f, highlightYTimer.getValueF(), DROPDOWN_WIDTH, ITEM_HEIGHT, 3.0f,
                    ThemeHelper.overlay(alpha * highlightAmount * 0.1f));
        }
        float hoverAmount = hoverTimer.getValueF();
        Renderer.drawRoundedRect(poseStack, x + 6.0f, dropdownY, DROPDOWN_WIDTH, ITEM_HEIGHT, 3.0f,
                ColorUtil.withAlpha(ThemeHelper.controlHoverRgb(hoverAmount), alpha));
        String value = setting.getValue();
        if (value != null) {
            FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, value, x + 60.0f,
                    dropdownY + (ITEM_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f, ThemeHelper.foreground(alpha * 0.8f));
        }
        String arrowIcon = String.valueOf('\ueb5d');
        FontStore.MATERIAL_20.drawStringCentered(poseStack, arrowIcon,
                x + 6.0f + DROPDOWN_WIDTH - FontStore.MATERIAL_20.getStringWidth(arrowIcon) + 2.0f,
                dropdownY + (ITEM_HEIGHT - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 0.5f, ThemeHelper.foreground(alpha * 0.8f));
    }

    @Override
    public float getHeight() {
        return 36 + (isOpen ? 14 * setting.getModes().length : 0);
    }

    @Override
    public float getAnimatedHeight() {
        return 36.0f + (14 * setting.getModes().length) * visTimer.getValueF();
    }

    // ------------------------------------------------------------------
    // 几何查询 + 动作（供 GuiInputRouter 调用）
    // ------------------------------------------------------------------

    /** 命中查询：下拉首行（当前值那一行）。 */
    public boolean dropdownContains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, x + 6.0f, dropdownY(), DROPDOWN_WIDTH, ITEM_HEIGHT);
    }

    /**
     * 命中查询：展开列表中的某一项，返回其档位名；未命中返回 null。
     * 判定几何与 render 里的高亮判定一致（逐项按 itemY − hoverOffset 的行框）。
     */
    public String itemAt(double mouseX, double mouseY) {
        if (!isOpen) {
            return null;
        }
        float hoverOffset = (ITEM_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f;
        float itemY = dropdownY() + hoverOffset + ITEM_HEIGHT;
        for (String mode : setting.getModes()) {
            if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, x + 6.0f, itemY - hoverOffset, DROPDOWN_WIDTH, ITEM_HEIGHT)) {
                return mode;
            }
            itemY += ITEM_HEIGHT;
        }
        return null;
    }

    public boolean isOpen() {
        return isOpen;
    }

    /** 展开 / 收起下拉。 */
    public void toggleOpen() {
        isOpen = !isOpen;
    }

    /** 选中某档位并收起下拉。 */
    public void select(String mode) {
        setting.setValue(mode);
        isOpen = false;
        hoveredMode = null;
    }
}
