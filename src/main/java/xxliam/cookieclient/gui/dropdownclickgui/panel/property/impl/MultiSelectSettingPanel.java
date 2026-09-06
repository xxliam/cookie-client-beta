package xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.PropertyPanel;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.MultiSelectSetting;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 多选 chips 行（Opal {@code MultipleBooleanPropertyComponent}）：选项自动换行成小圆角块
 * （medium 6 字号），点击翻转选中；选中块 = 主题主色 40%，未选中 = 0xff818582 暗 0.6，
 * 文字选中白 90% / 未选灰。外框 1.5px 描边圆角块。
 */
public class MultiSelectSettingPanel extends PropertyPanel {

    private static final int MUTED_COLOR = 0xFF818582;

    private final MultiSelectSetting setting;

    public MultiSelectSettingPanel(final MultiSelectSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        super.render(guiGraphics, mouseX, mouseY, delta, alpha);

        label(guiGraphics, setting.getName(), x + 5.0f, y + 8.5f, alpha);

        // 外框尺寸由换行布局决定（第一遍只量高度）
        float addedHeight = layout(false, guiGraphics, alpha);
        float boxX = x + 5.0f;
        float boxY = y + 13.0f;
        float boxWidth = width - 10.0f;
        float boxHeight = 10.0f + addedHeight;
        float radius = 2.5f;

        // 描边：外整块 0xff505050，内缩 1.5px 实底 0xff191919
        Renderer.drawRoundedRect(guiGraphics.pose(), boxX, boxY, boxWidth, boxHeight, radius,
                ColorUtil.withAlpha(0xFF505050, alpha));
        Renderer.drawRoundedRect(guiGraphics.pose(), boxX + 1.5f, boxY + 1.5f, boxWidth - 3.0f, boxHeight - 3.0f,
                radius, ColorUtil.withAlpha(0xFF191919, alpha));

        setHeight(DEFAULT_HEIGHT + boxHeight);

        // 第二遍真正画 chips
        layout(true, guiGraphics, alpha);
    }

    /** 换行布局 chips；render=true 时绘制。返回内容占用高度。 */
    private float layout(boolean render, GuiGraphics g, float alpha) {
        CustomFont font = FontStore.PRODUCTSANS_MEDIUM_6;
        float addedHeight = 0.0f;
        float currentLineLength = 2.0f;
        int themeFirst = ThemeHelper.getThemeColors()[0];

        for (String option : setting.getOptions()) {
            float elementWidth = font.getStringWidth(option) + 8.75f;
            if (currentLineLength + elementWidth > width - 10.0f) {
                addedHeight += 10.0f;
                currentLineLength = 2.0f;
            }
            if (render) {
                boolean active = setting.isSelected(option);
                float chipX = x + 4.5f + currentLineLength;
                float chipY = y + 13.0f + addedHeight + 7.0f - 5.5f;
                int bg = active
                        ? ColorUtil.applyOpacity(themeFirst, 0.4f * alpha)
                        : ColorUtil.withAlpha(ColorUtil.darker(MUTED_COLOR, 0.6f), alpha);
                Renderer.drawRoundedRect(g.pose(), chipX, chipY, elementWidth - 4.0f, 8.5f, 2.5f, bg);
                int textColor = active
                        ? ColorUtil.applyOpacity(-1, 0.9f * alpha)
                        : ColorUtil.applyOpacity(0xFFAAAAAA, alpha);
                baseline(g, font, option, x + 7.0f + currentLineLength, y + 13.0f + addedHeight + 8.0f, textColor);
            }
            currentLineLength += elementWidth - 2.5f;
        }
        return addedHeight + 1.5f;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        CustomFont font = FontStore.PRODUCTSANS_MEDIUM_6;
        float addedHeight = 0.0f;
        float currentLineLength = 2.0f;

        for (String option : setting.getOptions()) {
            float elementWidth = font.getStringWidth(option) + 8.75f;
            if (currentLineLength + elementWidth > width - 10.0f) {
                addedHeight += 10.0f;
                currentLineLength = 2.0f;
            }
            float hitX = x + 4.5f + currentLineLength;
            float hitY = y + 13.0f + addedHeight + 7.0f - 5.5f;
            if (isHovering(hitX, hitY, elementWidth - 4.0f, 8.5f, mouseX, mouseY)) {
                List<String> next = new ArrayList<>(setting.getValue());
                if (next.contains(option)) {
                    next.remove(option);
                } else {
                    next.add(option);
                }
                setting.setValue(next);
                return;
            }
            currentLineLength += elementWidth - 2.5f;
        }
    }
}
