package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.MultiSelectSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.ArrayList;

/**
 * 多选设置控件：点击展开多选项，可勾选多个。
 * <p>
 * 纯视图：绘制 + 几何查询（{@link #dropdownContains} / {@link #itemAt}）+ 语义化动作
 * （{@link #toggleOpen()} / {@link #toggleOption(String)}）。
 */
public class MultiSelectSettingElement extends SettingElement<MultiSelectSetting> {

    private static final String ELLIPSIS = "...";
    private static final float DROPDOWN_WIDTH = 108.0f;
    private static final float ITEM_HEIGHT = 14.0f;

    private final SmoothAnimationTimer hoverTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer visTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer highlightTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer highlightYTimer = new SmoothAnimationTimer();
    private boolean isDropdownHovered;
    private boolean isOpen;
    private String hoveredOption;
    private boolean hasMultipleSelected;

    public MultiSelectSettingElement(CategoryPanel parentPanel, MultiSelectSetting setting) {
        super(parentPanel, setting);
    }

    /** 下拉首行 y（与绘制共用同一公式）。 */
    private float dropdownY() {
        return y + 18.0f + 2.0f;
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float dropdownY = dropdownY();
        hoveredOption = null;
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
        float nameY = y + (18.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, setting.getName(), x + 6.0f, nameY, ThemeHelper.foreground(alpha * 0.8f));
        float openAmount = visTimer.getValueF();
        if (openAmount > 0.0f) {
            float dropdownHeight = ITEM_HEIGHT + setting.getOptions().size() * ITEM_HEIGHT * openAmount;
            Renderer.drawRoundedRect(poseStack, x + 6.0f, dropdownY, DROPDOWN_WIDTH, dropdownHeight, 3.0f,
                    ThemeHelper.control(alpha * openAmount));
            float hoverOffset = (ITEM_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f;
            float itemY = dropdownY + hoverOffset + ITEM_HEIGHT;
            for (String option : setting.getOptions()) {
                if (CursorUtil.isInBounds(mouseX, mouseY, x + 6.0f, itemY - hoverOffset, DROPDOWN_WIDTH, ITEM_HEIGHT)) {
                    hoveredOption = option;
                    highlightYTimer.animate(itemY - hoverOffset, 0.2, Easings.EASE_OUT_POW2);
                }
                if (dropdownY + dropdownHeight > itemY + FontStore.AXIFORMA_BOLD_13.getFontHeight()) {
                    FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, option, x + 60.0f, itemY, ThemeHelper.foreground(alpha * 0.8f * openAmount));
                    if (setting.getValue().contains(option)) {
                        FontStore.MATERIAL_14.drawString(poseStack, "\ueb73",
                                x + 6.0f + DROPDOWN_WIDTH - FontStore.MATERIAL_14.getStringWidth("\ueb73") - 4.0f,
                                itemY - hoverOffset + (ITEM_HEIGHT - FontStore.MATERIAL_14.getFontHeight()) / 2.0f + 0.5f,
                                ThemeHelper.foreground(alpha * 0.56f * openAmount));
                    }
                }
                itemY += ITEM_HEIGHT;
            }
        }
        highlightTimer.animate(hoveredOption == null || !isOpen ? 0.0 : 1.0, 0.18, Easings.EASE_OUT_POW2);
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
        String selectedLabel = setting.getValue().isEmpty() ? "..." : setting.getValue().get(0);
        hasMultipleSelected = false;
        if (setting.getValue().size() > 1) {
            selectedLabel = selectedLabel + ELLIPSIS;
            hasMultipleSelected = true;
        }
        FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, selectedLabel, x + 60.0f,
                dropdownY + (ITEM_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f, ThemeHelper.foreground(alpha * 0.8f));
        String arrowIcon = String.valueOf('\ueb5d');
        FontStore.MATERIAL_20.drawString(poseStack, arrowIcon,
                x + 6.0f + DROPDOWN_WIDTH - FontStore.MATERIAL_20.getStringWidth(arrowIcon) - 2.0f,
                dropdownY + (ITEM_HEIGHT - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 0.5f, ThemeHelper.foreground(alpha * 0.8f));
        if (isDropdownHovered && hasMultipleSelected) {
            parentPanel.setHoveredSettingElement(this);
            parentPanel.setTooltipText(setting.getValue().toString());
            parentPanel.setShowTooltip(true);
        } else if (parentPanel.getHoveredSettingElement() == this) {
            parentPanel.setShowTooltip(false);
            parentPanel.setHoveredSettingElement(null);
        }
    }

    @Override
    public float getHeight() {
        return 36 + (isOpen ? 14 * setting.getOptions().size() : 0);
    }

    @Override
    public float getAnimatedHeight() {
        return 36.0f + (14 * setting.getOptions().size()) * visTimer.getValueF();
    }

    // ------------------------------------------------------------------
    // 几何查询 + 动作（供 GuiInputRouter 调用）
    // ------------------------------------------------------------------

    /** 命中查询：下拉首行（当前选中值那一行）。 */
    public boolean dropdownContains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, x + 6.0f, dropdownY(), DROPDOWN_WIDTH, ITEM_HEIGHT);
    }

    /** 命中查询：展开列表中的某一项，返回选项名；未命中返回 null。 */
    public String itemAt(double mouseX, double mouseY) {
        if (!isOpen) {
            return null;
        }
        float hoverOffset = (ITEM_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f;
        float itemY = dropdownY() + hoverOffset + ITEM_HEIGHT;
        for (String option : setting.getOptions()) {
            if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, x + 6.0f, itemY - hoverOffset, DROPDOWN_WIDTH, ITEM_HEIGHT)) {
                return option;
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

    /** 勾选 / 取消某选项（至少保留一项：已是唯一选中项时取消无效，与原行为一致）。 */
    public void toggleOption(String option) {
        ArrayList<String> selected = new ArrayList<>(setting.getValue());
        if (selected.contains(option)) {
            if (selected.size() > 1) {
                selected.remove(option);
            }
        } else {
            selected.add(option);
        }
        setting.setValue(selected);
    }
}
