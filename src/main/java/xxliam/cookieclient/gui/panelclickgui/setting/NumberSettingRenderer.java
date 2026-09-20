package xxliam.cookieclient.gui.panelclickgui.setting;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.panelclickgui.PanelClickGui;
import xxliam.cookieclient.gui.panelclickgui.support.PanelFonts;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;
import xxliam.cookieclient.utils.render.TextGlow;

public class NumberSettingRenderer
implements SettingRenderer {
    private static NumberSetting editingNumberSetting;
    private static String editingText;
    private static long lastInputTime;
    private final Map<NumberSetting, Long> editIconTimers = new HashMap<>();
    private final Map<NumberSetting, Boolean> plusButtonHover = new HashMap<>();
    private final Map<NumberSetting, Boolean> minusButtonHover = new HashMap<>();
    private final Map<NumberSetting, Boolean> editIconHover = new HashMap<>();

    @Override
    public int render(GuiGraphics guiGraphics, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, float alpha, float scale) {
        if (!(setting instanceof NumberSetting numberSetting)) {
            return 0;
        }
        boolean editing = numberSetting.equals(editingNumberSetting);
        int rowHeight = Math.round(24.0f * scale);
        int sidePadding = Math.round(12.0f * scale);
        int innerPadding = Math.round(8.0f * scale);
        CustomFont valueFont = PanelFonts.axiformaBold(14.0f * scale);
        String valueText = editing ? editingText : this.formatValue(numberSetting.getValue().doubleValue());
        float valueWidth = RenderHelper.getStringWidth(valueFont, valueText);
        int widgetWidth = sidePadding * 2 + (int)valueWidth + innerPadding * 2 - Math.round(2.0f * scale);
        int widgetX = x + width - widgetWidth;
        int widgetHeight = rowHeight - Math.round(14.0f * scale);
        float centerY = (float)y + (float)rowHeight / 2.0f;
        int widgetY = Math.round(centerY - (float)widgetHeight / 2.0f);
        int iconSize = Math.round(16.0f * scale);
        int iconGap = Math.round(4.0f * scale);
        int iconX = widgetX - iconSize - iconGap;
        int iconY = Math.round(centerY - (float)iconSize / 2.0f);
        this.updateHoverStates(numberSetting, mouseX, mouseY, widgetX, widgetY, widgetWidth, sidePadding, widgetHeight, iconX, iconY, iconSize);
        this.drawNumberWidget(guiGraphics, numberSetting, x, y, width, widgetX, widgetY, widgetWidth, widgetHeight, iconX, iconY, editing, alpha, scale);
        return rowHeight;
    }

    @Override
    public boolean onClick(Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, int button, float scale) {
        if (!(setting instanceof NumberSetting numberSetting) || button != 0) {
            return false;
        }
        int rowHeight = Math.round(24.0f * scale);
        int sidePadding = Math.round(12.0f * scale);
        int innerPadding = Math.round(8.0f * scale);
        CustomFont valueFont = PanelFonts.axiformaBold(14.0f * scale);
        String valueText = this.formatValue(numberSetting.getValue().doubleValue());
        float valueWidth = RenderHelper.getStringWidth(valueFont, valueText);
        int widgetWidth = sidePadding * 2 + (int)valueWidth + innerPadding * 2 - Math.round(2.0f * scale);
        int widgetX = x + width - widgetWidth;
        int widgetHeight = rowHeight - Math.round(14.0f * scale);
        float centerY = (float)y + (float)rowHeight / 2.0f;
        int widgetY = Math.round(centerY - (float)widgetHeight / 2.0f);
        int iconSize = Math.round(16.0f * scale);
        int iconGap = Math.round(4.0f * scale);
        int iconX = widgetX - iconSize - iconGap;
        int iconY = Math.round(centerY - (float)iconSize / 2.0f);
        if (mouseX >= iconX && mouseX <= iconX + iconSize && mouseY >= iconY && mouseY <= iconY + iconSize) {
            this.startEditing(numberSetting);
            return true;
        }
        if (mouseX >= widgetX && mouseX <= widgetX + widgetWidth && mouseY >= widgetY && mouseY <= widgetY + widgetHeight) {
            if (mouseX < widgetX + sidePadding) {
                this.decrementValue(numberSetting);
            } else if (mouseX > widgetX + widgetWidth - sidePadding) {
                this.incrementValue(numberSetting);
            }
            return true;
        }
        return false;
    }

    public static void clearEditing() {
        NumberSettingRenderer.cancelEdit();
    }

    public static boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (editingNumberSetting == null) {
            return false;
        }
        if (keyCode == 257 || keyCode == 335) {
            NumberSettingRenderer.commitEdit();
            return true;
        }
        if (keyCode == 256) {
            NumberSettingRenderer.cancelEdit();
            return true;
        }
        if (keyCode == 259 && !editingText.isEmpty()) {
            editingText = editingText.substring(0, editingText.length() - 1);
            lastInputTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public static boolean onCharTyped(char c) {
        if (editingNumberSetting == null) {
            return false;
        }
        if (Character.isDigit(c) || c == '.' || c == '-' && editingText.isEmpty()) {
            if (c == '.' && editingText.contains(".")) {
                return true;
            }
            editingText = editingText + c;
            lastInputTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    public void onMouseMove(double mouseX, double mouseY) {
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
    }

    private void startEditing(NumberSetting numberSetting) {
        editingNumberSetting = numberSetting;
        editingText = "";
        lastInputTime = System.currentTimeMillis();
    }

    private static void commitEdit() {
        if (editingNumberSetting == null || editingText.isEmpty()) {
            NumberSettingRenderer.cancelEdit();
            return;
        }
        try {
            double parsed = Double.parseDouble(editingText);
            double min = editingNumberSetting.getMin().doubleValue();
            double max = editingNumberSetting.getMax().doubleValue();
            parsed = Math.max(min, Math.min(max, parsed));
            NumberSettingRenderer.applyValueStatic(editingNumberSetting, parsed);
            PanelClickGui.INSTANCE.addToast(editingNumberSetting.getName() + " set to " + String.format(Locale.US, "%.1f", new Object[]{parsed}));
        } catch (NumberFormatException numberFormatException) {
            PanelClickGui.INSTANCE.addToast("Invalid input, edit cancelled");
        }
        NumberSettingRenderer.cancelEdit();
    }

    private static void cancelEdit() {
        editingNumberSetting = null;
        editingText = "";
    }

    private int applyAlpha(int color, float alpha) {
        int origAlpha = color >> 24 & 0xFF;
        int newAlpha = (int)((float)origAlpha * alpha);
        return newAlpha << 24 | color & 0xFFFFFF;
    }

    private void drawNumberWidget(GuiGraphics guiGraphics, NumberSetting numberSetting, int x, int y, int width, int widgetX, int widgetY, int widgetWidth, int widgetHeight, int iconX, int iconY, boolean editing, float alpha, float scale) {
        String displayText;
        float rowHeight = 24.0f * scale;
        float centerY = (float)y + rowHeight / 2.0f;
        CustomFont nameFont = PanelFonts.axiformaRegular(14.0f * scale);
        CustomFont valueFont = PanelFonts.axiformaBold(14.0f * scale);
        CustomFont signFont = PanelFonts.axiformaBold(12.0f * scale);

        float nameY = centerY - RenderHelper.getCapHeight(nameFont) / 2.0f;
        TextGlow.drawGlowText(guiGraphics.pose(), nameFont, numberSetting.getName(), x, nameY, this.applyAlpha(-1, alpha), this.applyAlpha(new Color(255, 255, 255, 120).getRGB(), alpha), 8.0f * scale);
        this.drawEditIcon(guiGraphics, iconX, iconY, numberSetting, alpha, scale);
        
        int sidePadding = Math.round(12.0f * scale);
        Renderer.drawRoundedRect(guiGraphics.pose(), widgetX, widgetY, widgetWidth, widgetHeight, 5.0f * scale, this.applyAlpha(0x50F5F5F5, alpha));
        int middleX = widgetX + sidePadding;
        int middleWidth = widgetWidth - sidePadding * 2;
        Renderer.drawRoundedRect(guiGraphics.pose(), middleX, widgetY, middleWidth, widgetHeight, 0.0f, this.applyAlpha(1086900424, alpha));
        
        float widgetCenterY = widgetY + widgetHeight / 2.0f;
        float signY = widgetCenterY - RenderHelper.getCapHeight(signFont) / 2.0f;
        
        int minusColor = this.minusButtonHover.getOrDefault(numberSetting, false) ? new Color(255, 255, 255).getRGB() : -1;
        RenderHelper.drawText(guiGraphics.pose(), signFont, "-", widgetX + sidePadding / 2.0f - RenderHelper.getStringWidth(signFont, "-") / 2.0f, signY, this.applyAlpha(minusColor, alpha));
        
        int plusColor = this.plusButtonHover.getOrDefault(numberSetting, false) ? new Color(255, 255, 255).getRGB() : -1;
        RenderHelper.drawText(guiGraphics.pose(), signFont, "+", widgetX + widgetWidth - sidePadding + sidePadding / 2.0f - RenderHelper.getStringWidth(signFont, "+") / 2.0f, signY, this.applyAlpha(plusColor, alpha));
        
        displayText = editing ? editingText : this.formatValue(numberSetting.getValue().doubleValue());
        if (editing && displayText.isEmpty()) {
            displayText = "0";
        }
        
        float displayWidth = RenderHelper.getStringWidth(valueFont, displayText);
        float displayX = widgetX + widgetWidth / 2.0f - displayWidth / 2.0f;
        float displayY = widgetCenterY - RenderHelper.getCapHeight(valueFont) / 2.0f;
        
        if (editing) {
            long now = System.currentTimeMillis();
            float cyclePos = (float)(now % 1000L) / 1000.0f;
            float sineWave = (float)(Math.sin((double)cyclePos * Math.PI * 2.0) * 0.5 + 0.5);
            int textAlpha = (int)(255.0f * (0.6f + sineWave * 0.4f) * alpha);
            int textColor = textAlpha << 24 | 0xFFFFFF;
            RenderHelper.drawText(guiGraphics.pose(), valueFont, displayText, displayX, displayY, textColor);
            
            float caretX = displayX + displayWidth + 2.0f * scale;
            int caretAlpha = (int)(255.0f * sineWave * alpha);
            int caretColor = caretAlpha << 24 | 0xFFFFFF;
            float caretHeight = RenderHelper.getCapHeight(valueFont);
            Renderer.drawFilledRect(guiGraphics.pose(), caretX, displayY, Math.max(1.0f, Math.round(scale)), caretHeight, caretColor);
        } else {
            int glowColor = new Color(255, 255, 255, 120).getRGB();
            TextGlow.drawGlowText(guiGraphics.pose(), valueFont, displayText, displayX, displayY, this.applyAlpha(-1, alpha), this.applyAlpha(glowColor, alpha), 6.0f * scale);
        }
    }

    private void drawEditIcon(GuiGraphics guiGraphics, int iconX, int iconY, NumberSetting numberSetting, float alpha, float scale) {
        boolean hovered = this.editIconHover.getOrDefault(numberSetting, false);
        long timerStart = this.editIconTimers.getOrDefault(numberSetting, 0L);
        long sinceChange = System.currentTimeMillis() - timerStart;
        int iconSize = Math.round(16.0f * scale);
        float progress = Math.min(1.0f, (float)sinceChange / 200.0f);
        if (!hovered) {
            progress = 1.0f - progress;
        }
        int colorFrom = -5197648;
        int colorTo = -1;
        int iconColor = ColorUtil.lerpColorHSB(colorFrom, colorTo, progress);
        CustomFont iconFont = PanelFonts.materialIcons(iconSize);
        String iconText = "\uE3C9";
        float iconTextWidth = RenderHelper.getStringWidth(iconFont, iconText);
        
        float drawX = iconX + (iconSize - iconTextWidth) / 2.0f;
        float drawY = iconY + iconSize / 2.0f - RenderHelper.getCapHeight(iconFont) / 2.0f;
        
        RenderHelper.drawText(guiGraphics.pose(), iconFont, iconText, drawX, drawY, this.applyAlpha(iconColor, alpha));
    }

    private void incrementValue(NumberSetting numberSetting) {
        double current = numberSetting.getValue().doubleValue();
        double step = numberSetting.getStep().doubleValue();
        double max = numberSetting.getMax().doubleValue();
        double newValue = Math.min(max, current + step);
        this.applyValue(numberSetting, newValue);
        PanelClickGui.INSTANCE.addToast(numberSetting.getName() + " set to " + this.formatValue(newValue));
    }

    private void decrementValue(NumberSetting numberSetting) {
        double current = numberSetting.getValue().doubleValue();
        double step = numberSetting.getStep().doubleValue();
        double min = numberSetting.getMin().doubleValue();
        double newValue = Math.max(min, current - step);
        this.applyValue(numberSetting, newValue);
        PanelClickGui.INSTANCE.addToast(numberSetting.getName() + " set to " + this.formatValue(newValue));
    }

    private void applyValue(NumberSetting numberSetting, double value) {
        if (numberSetting.getValue() instanceof Integer) {
            numberSetting.setValue((int)Math.round(value));
        } else if (numberSetting.getValue() instanceof Long) {
            numberSetting.setValue(Math.round(value));
        } else if (numberSetting.getValue() instanceof Float) {
            numberSetting.setValue((float)value);
        } else {
            numberSetting.setValue(value);
        }
    }

    private static void applyValueStatic(NumberSetting numberSetting, double value) {
        if (numberSetting.getValue() instanceof Integer) {
            numberSetting.setValue((int)Math.round(value));
        } else if (numberSetting.getValue() instanceof Long) {
            numberSetting.setValue(Math.round(value));
        } else if (numberSetting.getValue() instanceof Float) {
            numberSetting.setValue((float)value);
        } else {
            numberSetting.setValue(value);
        }
    }

    private void updateHoverStates(NumberSetting numberSetting, int mouseX, int mouseY, int widgetX, int widgetY, int widgetWidth, int sidePadding, int widgetHeight, int iconX, int iconY, int iconSize) {
        boolean iconHovered;
        boolean overWidget = mouseX >= widgetX && mouseX <= widgetX + widgetWidth && mouseY >= widgetY && mouseY <= widgetY + widgetHeight;
        this.minusButtonHover.put(numberSetting, overWidget && mouseX < widgetX + sidePadding);
        this.plusButtonHover.put(numberSetting, overWidget && mouseX > widgetX + widgetWidth - sidePadding);
        boolean dup = iconHovered = mouseX >= iconX && mouseX <= iconX + iconSize && mouseY >= iconY && mouseY <= iconY + iconSize;
        if (iconHovered != this.editIconHover.getOrDefault(numberSetting, false)) {
            this.editIconHover.put(numberSetting, iconHovered);
            this.editIconTimers.put(numberSetting, System.currentTimeMillis());
        }
    }

    private String formatValue(double value) {
        return String.format(Locale.US, "%.1f", new Object[]{value});
    }

    @Override
    public boolean supports(Setting<?> setting) {
        return setting instanceof NumberSetting;
    }

    @Override
    public int getHeight(Setting<?> setting, float scale) {
        return Math.round(24.0f * scale);
    }

    static {
        editingText = "";
        lastInputTime = 0L;
    }
}
