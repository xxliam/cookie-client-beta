package xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownRender;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.PropertyPanel;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.modules.impl.render.Theme;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ClientTheme;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;

/**
 * 模式下拉行（Opal {@code ModePropertyComponent}）：名称行 + 下拉选项框（右键展开，
 * 125ms DECELERATE 动画），展开后 13px/行列出其余档位；选项若是 Theme 模块的主题档，
 * 跟随显示该主题主副色 swatch（opal isTheme 分支，视觉照搬）。
 */
public class ModeSettingPanel extends PropertyPanel {

    private final ModeSetting setting;
    private final Module owner;

    private SmoothAnimationTimer expandAnim;
    private boolean expanded;

    public ModeSettingPanel(final ModeSetting setting, final Module owner) {
        super(setting);
        this.setting = setting;
        this.owner = owner;
    }

    @Override
    public void init() {
        expandAnim = null;
        expanded = false;
    }

    private boolean isTheme() {
        return owner instanceof Theme && setting.getName().equals("Theme");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        super.render(guiGraphics, mouseX, mouseY, delta, alpha);

        if (expandAnim == null) {
            expandAnim = new SmoothAnimationTimer();
            expandAnim.setCurrentValue(expanded ? 1.0 : 0.0);
        } else {
            expandAnim.animate(expanded ? 1.0 : 0.0, 0.125, Easings.EASE_OUT_QUAD);
            expandAnim.tick();
        }
        float anim = expandAnim.getValueF();

        CustomFont font = FontStore.PRODUCTSANS_MEDIUM_7;
        CustomFont fontBold = FontStore.PRODUCTSANS_BOLD_7;
        label(guiGraphics, setting.getName(), x + 5.0f, y + 9.5f, alpha);

        float padding = 2.0f;
        float rectX = x + 3.0f;
        float rectY = y + padding + 11.5f;
        float rectWidth = width - 5.0f - padding;
        float rectHeight = height - padding - (32.0f - DEFAULT_HEIGHT);
        if (rectHeight > 0.0f) {
            Renderer.drawRoundedRect(guiGraphics.pose(), rectX, rectY, rectWidth, rectHeight, 4.0f,
                    ColorUtil.applyOpacity(0xFF000000, 0.25f * alpha));
        }
        String value = setting.getValue() != null ? setting.getValue() : "";
        baseline(guiGraphics, fontBold, value, rectX + 4.0f, rectY + 10.0f, ColorUtil.withAlpha(-1, alpha));

        if (isTheme()) {
            int[] colors = ClientTheme.fromName(value).getStaticColors();
            float valueWidth = fontBold.getStringWidth(value);
            Renderer.drawRoundedRect(guiGraphics.pose(), rectX + valueWidth + 7.0f, rectY + 4.0f, 7.0f, 7.0f,
                    2.0f, ColorUtil.withAlpha(colors[0], alpha));
            Renderer.drawRoundedRect(guiGraphics.pose(), rectX + valueWidth + 16.0f, rectY + 4.0f, 7.0f, 7.0f,
                    2.0f, ColorUtil.withAlpha(colors[1], alpha));
        }

        // 展开箭头（materialicons 9，旋转 180×anim）
        String expandIcon = "\ue5cf";
        float iconSize = 9.0f;
        float iconWidth = FontStore.MATERIALICONS_9.getStringWidth(expandIcon);
        float iconCx = rectX + rectWidth - 12.0f + iconWidth / 2.0f;
        float iconCy = rectY + 2.5f + iconSize / 2.0f;
        RenderHelper.pushRotateAround(guiGraphics.pose(), iconCx, iconCy, anim * 180.0f);
        DropdownRender.iconCentered(guiGraphics, FontStore.MATERIALICONS_9, expandIcon, iconCx, iconCy,
                ColorUtil.withAlpha(-1, alpha));
        RenderHelper.popPose(guiGraphics.pose());

        // 下拉选项区（scissor 到选框区域）
        if (rectHeight > 0.0f) {
            Renderer.pushScissor(Math.round(rectX), Math.round(rectY), Math.round(rectWidth), Math.round(rectHeight));
            float addedHeight = 0.0f;
            if (anim > 0.0f) {
                for (String mode : setting.getModes()) {
                    if (mode == null || mode.equals(setting.getValue())) {
                        continue;
                    }
                    baseline(guiGraphics, font, mode, rectX + 4.0f, rectY + 9.5f + 13.0f + addedHeight,
                            ColorUtil.withAlpha(-1, alpha * anim));
                    if (isTheme()) {
                        int[] colors = ClientTheme.fromName(mode).getStaticColors();
                        Renderer.drawRoundedRect(guiGraphics.pose(),
                                rectX + width - 5.0f - padding - 20.5f, rectY + 3.5f + 13.0f + addedHeight, 7.0f, 7.0f, 2.5f,
                                ColorUtil.withAlpha(colors[0], alpha * anim));
                        Renderer.drawRoundedRect(guiGraphics.pose(),
                                rectX + width - 5.0f - padding - 12.0f, rectY + 3.5f + 13.0f + addedHeight, 7.0f, 7.0f, 2.5f,
                                ColorUtil.withAlpha(colors[1], alpha * anim));
                    }
                    addedHeight += 13.0f;
                }
            }
            setHeight(32.0f + addedHeight * anim);
            Renderer.popScissor();
        } else {
            setHeight(32.0f);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(x, y, width, 32.0f, mouseX, mouseY) && button == 1) {
            expanded = !expanded;
            return;
        }
        if (!expanded) {
            return;
        }
        float padding = 2.0f;
        float rectX = x + 3.0f;
        float rectY = y + padding + 11.5f;
        float rectWidth = width - 8.0f - padding - 3.0f;

        float addedHeight = 0.0f;
        for (String mode : setting.getModes()) {
            if (mode == null || mode.equals(setting.getValue())) {
                continue;
            }
            if (isHovering(rectX, rectY + 13.0f + addedHeight, rectWidth, 13.0f, mouseX, mouseY)) {
                setting.setValue(mode);
                expanded = false;
                return;
            }
            addedHeight += 13.0f;
        }
    }
}
