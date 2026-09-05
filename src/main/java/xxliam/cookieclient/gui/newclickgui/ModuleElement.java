package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.NewClickGui;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ColorSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.MultiSelectSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块按钮：显示模块名、开关状态，右键展开设置项。
 */
public class ModuleElement extends UIElement {

    public static final int BG_COLOR = ColorUtil.fromRGB(32, 32, 32);

    private final List<SettingElement<?>> settingElements = new ArrayList<>();
    private final CategoryPanel parentPanel;
    private final Module module;
    private final BindElement bindElement;
    private final SmoothAnimationTimer enabledTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer hoveredTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer expandTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer settingsHeightTimer = new SmoothAnimationTimer();
    private float posX;
    private float posY;
    private float totalHeight = 20.0f;
    private float scrollOffset;
    private boolean isHovered;
    private boolean isExpanded;

    public ModuleElement(CategoryPanel parentPanel, Module module) {
        this.parentPanel = parentPanel;
        this.module = module;
        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting booleanSetting) {
                settingElements.add(new BooleanSettingElement(parentPanel, booleanSetting));
            } else if (setting instanceof ModeSetting modeSetting) {
                settingElements.add(new ModeSettingElement(parentPanel, modeSetting));
            } else if (setting instanceof MultiSelectSetting multiSelectSetting) {
                settingElements.add(new MultiSelectSettingElement(parentPanel, multiSelectSetting));
            } else if (setting instanceof NumberSetting numberSetting) {
                settingElements.add(new NumberSettingElement(parentPanel, numberSetting));
            } else if (setting instanceof ColorSetting colorSetting) {
                settingElements.add(new ColorSettingElement(parentPanel, colorSetting));
            }
        }
        this.bindElement = new BindElement(parentPanel, module);
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float settingsTotalHeight = 0.0f;
        for (SettingElement<?> settingElement : settingElements) {
            if (!settingElement.getSetting().getVisibility().displayable()) {
                continue;
            }
            settingsTotalHeight += settingElement.getHeight();
        }
        // Bind 按钮固定在展开区域底部，一并计入展开高度
        settingsTotalHeight += bindElement.getHeight();
        settingsHeightTimer.animate(settingsTotalHeight, 0.2, Easings.EASE_OUT_POW2);
        settingsHeightTimer.tick();
        parentPanel.setCollapsed(!settingsHeightTimer.isDone());
        hoveredTimer.animate(isHovered ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        hoveredTimer.tick();
        enabledTimer.animate(module.isEnabled() ? 1.0 : 0.0, 0.3, Easings.EASE_OUT_POW2);
        enabledTimer.tick();
        expandTimer.animate(isExpanded ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW3);
        expandTimer.tick();
        float expandAmount = expandTimer.getValueF();
        totalHeight = 20.0f + expandAmount * settingsHeightTimer.getValueF();
        isHovered = parentPanel.equals(NewClickGui.focusedPanel) && CursorUtil.isInBounds(mouseX, mouseY, posX, posY, 120.0f, totalHeight);
        Renderer.drawFilledRect(poseStack, posX + 1.0f, posY + 20.0f, 118.0f, totalHeight - 20.0f, ColorUtil.withAlpha(BG_COLOR, expandAmount * alpha));
        float hoverAmount = hoveredTimer.getValueF();
        if (hoverAmount > 0.0f) {
            Renderer.drawFilledRect(poseStack, posX + 0.5f, posY, 119.0f, 20.0f, ColorUtil.withAlpha(-1, 0.1f * alpha * hoverAmount));
        }
        float enabledAmount = enabledTimer.getValueF();
        if (1.0f - enabledAmount > 0.0f) {
            FontStore.AXIFORMA_REGULAR_16.drawStringCentered(poseStack, module.getName(), posX + 60.0f,
                    posY + (20.0f - FontStore.AXIFORMA_REGULAR_16.getFontHeight()) / 2.0f, ColorUtil.withAlpha(-1, alpha * (1.0f - enabledAmount) * 0.6f));
        }
        if (enabledAmount > 0.0f) {
            float titleY = posY + (20.0f - FontStore.AXIFORMA_BOLD_16.getFontHeight()) / 2.0f;
            // 启用态模块名文字：原硬编码青绿 #2DE8CA。与 ModuleList 行文字同款变色——
            // 主题双色经 interpolateColorsBackAndForth 随时间来回流动；phase 用模块名 hash
            // 错开（不同于 ModuleList 按行号 i*20 错开），避免所有面板模块齐闪。
            int[] themeColors = ThemeHelper.getThemeColors();
            int phase = Math.floorMod(module.getName().hashCode(), 360);
            int titleColor = ColorUtil.interpolateColorsBackAndForth(6, phase, themeColors[0], themeColors[1]);
            FontStore.AXIFORMA_BOLD_16.drawStringCentered(poseStack, module.getName(), posX + 60.0f, titleY,
                    ColorUtil.withAlpha(titleColor, alpha * enabledAmount));
        }
        if (!module.getSettings().isEmpty()) {
            String arrowIcon = String.valueOf('\ueb4e');
            float arrowWidth = FontStore.MATERIAL_20.getStringWidth(arrowIcon);
            float arrowX = posX + 120.0f - arrowWidth - 6.0f;
            float arrowY = posY + (20.0f - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 1.0f;
            RenderHelper.pushRotateAround(poseStack, arrowX + arrowWidth / 2.0f, arrowY + FontStore.MATERIAL_20.getFontHeight() / 2.0f - 1.0f, 180.0f * expandAmount);
            FontStore.MATERIAL_20.drawString(poseStack, arrowIcon, arrowX, arrowY, ColorUtil.withAlpha(-1, (0.8f - 0.3f * expandAmount) * alpha));
            RenderHelper.popPose(poseStack);
        }
        if (isExpanded) {
            float settingY = posY + 20.0f;
            for (SettingElement<?> settingElement : settingElements) {
                if (!settingElement.getSetting().getVisibility().displayable()) {
                    continue;
                }
                settingElement.setX(posX);
                settingElement.setY(settingY);
                settingElement.render(clickGui, guiGraphics, poseStack, mouseX, mouseY, alpha * expandAmount, partialTicks);
                settingY += settingElement.getAnimatedHeight() * settingElement.getVisibilityTimer().getValueF();
            }
            // Bind 按钮：渲染在所有设置项之后（展开区域底部）
            bindElement.setX(posX);
            bindElement.setY(settingY);
            bindElement.render(clickGui, guiGraphics, poseStack, mouseX, mouseY, alpha * expandAmount, partialTicks);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHovered) {
            return false;
        }
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY, 120.0f, 20.0f)) {
            if (button == 0) {
                module.setEnabled(!module.isEnabled());
            } else if (button == 1 && !module.getSettings().isEmpty()) {
                isExpanded = !isExpanded;
            }
            return true;
        }
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY + 20.0f, 120.0f, totalHeight - 20.0f)) {
            for (SettingElement<?> settingElement : settingElements) {
                if (settingElement.getSetting().getVisibility().displayable() && settingElement.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (bindElement.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return isHovered;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (SettingElement<?> settingElement : settingElements) {
            if (settingElement.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return bindElement.mouseReleased(mouseX, mouseY, button);
    }

    public List<SettingElement<?>> getSettingElements() {
        return settingElements;
    }

    public CategoryPanel getParentPanel() {
        return parentPanel;
    }

    public Module getModule() {
        return module;
    }

    public SmoothAnimationTimer getEnabledTimer() {
        return enabledTimer;
    }

    public boolean isHovered() {
        return isHovered;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    @Override
    public float getX() {
        return posX;
    }

    @Override
    public float getY() {
        return posY;
    }

    @Override
    public float getHeight() {
        return totalHeight;
    }

    @Override
    public void setX(float x) {
        this.posX = x;
    }

    @Override
    public void setY(float y) {
        this.posY = y;
    }

    @Override
    public void setHeight(float height) {
        this.totalHeight = height;
    }
}
