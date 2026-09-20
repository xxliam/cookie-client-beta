package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
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
 * <p>
 * 纯视图：绘制 + 几何查询（{@link #isWithinBounds} / {@link #titleContains} /
 * {@link #settingsAreaContains}）+ 语义化动作（{@link #toggleModule()} / {@link #toggleExpanded()}）。
 * 点击路由与拖动状态由 {@code input.GuiInputRouter} 处理。
 */
public class ModuleElement extends UIElement {

    public static final int BG_COLOR = ColorUtil.fromRGB(32, 32, 32);

    /**
     * 展开的「设置区」背景填充色：**完全透明** —— 模块行下方原本会铺一层比面板底色更亮的深灰
     * （{@link #BG_COLOR} = 32,32,32，盖在面板底色 23,23,23 上），现按需求改为不着色，
     * 展开区直接透出面板自身的底色。
     * <p>
     * 绘制调用**保留**（未删除），随时改回 {@code BG_COLOR} 即可恢复深灰底；
     * alpha 由 {@code withAlpha} 乘展开进度，收起时同样无形。
     */
    private static final int EXPAND_BG_COLOR = 0x00000000;

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
        // 展开的设置区背景：现为完全透明（EXPAND_BG_COLOR），仅保留调用
        Renderer.drawFilledRect(poseStack, posX + 1.0f, posY + 20.0f, 118.0f, totalHeight - 20.0f, ColorUtil.withAlpha(EXPAND_BG_COLOR, expandAmount * alpha));
        float hoverAmount = hoveredTimer.getValueF();
        if (hoverAmount > 0.0f) {
            Renderer.drawFilledRect(poseStack, posX + 0.5f, posY, 119.0f, 20.0f, ThemeHelper.overlay(0.1f * alpha * hoverAmount));
        }
        float enabledAmount = enabledTimer.getValueF();
        if (1.0f - enabledAmount > 0.0f) {
            FontStore.AXIFORMA_REGULAR_16.drawStringCentered(poseStack, module.getName(), posX + 60.0f,
                    posY + (20.0f - FontStore.AXIFORMA_REGULAR_16.getFontHeight()) / 2.0f, ThemeHelper.foreground(alpha * (1.0f - enabledAmount) * 0.6f));
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
        if (!module.getSettings().isEmpty() && !NewClickGui.displayingBinds) {
            String arrowIcon = String.valueOf('\ueb4e');
            float arrowWidth = FontStore.MATERIAL_20.getStringWidth(arrowIcon);
            float arrowX = posX + 120.0f - arrowWidth - 6.0f;
            float arrowY = posY + (20.0f - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 1.0f;
            RenderHelper.pushRotateAround(poseStack, arrowX + arrowWidth / 2.0f, arrowY + FontStore.MATERIAL_20.getFontHeight() / 2.0f - 1.0f, 180.0f * expandAmount);
            FontStore.MATERIAL_20.drawString(poseStack, arrowIcon, arrowX, arrowY, ThemeHelper.foreground((0.8f - 0.3f * expandAmount) * alpha));
            RenderHelper.popPose(poseStack);
        }
        // TAB 按住时显示绑定键名（自 opal 风格下拉 GUI 移植，该 GUI 已删除）：右对齐到展开箭头原本占的位置。
        // 行宽只有 120px，长键名（Left Control / Page Down / Mouse 4 …）走缩写，见 BindElement#getShortKeyName。
        if (NewClickGui.displayingBinds && module.getKeyBind() != 0) {
            String keyString = "[" + BindElement.getShortKeyName(module.getKeyBind()) + "]";
            float keyWidth = FontStore.AXIFORMA_BOLD_13.getStringWidth(keyString);
            FontStore.AXIFORMA_BOLD_13.drawString(poseStack, keyString,
                    posX + 120.0f - keyWidth - 6.0f,
                    posY + (20.0f - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f,
                    ThemeHelper.foreground(alpha));
        }
        // 设置区：只要动画高度还没归零就渲染（不能只判 isExpanded）。
        // 原来写成 if (isExpanded)，于是右键收起的那一帧 isExpanded 立刻变 false、内容整块瞬灭，
        // 而 totalHeight 还在按 0.2s 缩 —— 观感就是「收起时灰色下拉底直接没了、只剩空壳在缩」。
        // 现在改用动画高度做门槛，并配合 scissor 把内容裁到该高度内：收起时内容随区域一起被裁掉，
        // 顺带解决展开初期内容溢出到下方模块行上的问题。
        float settingsRegionHeight = Math.max(0.0f, totalHeight - 20.0f);
        if (settingsRegionHeight > 0.0f) {
            float guiScale = NewClickGui.scale();
            // scissor 不受 Pose 影响：走 pushScissorScreen（输入已含整体缩放系数，仅内部再 ×guiScale），
            // 与 CategoryPanel 的模块列表裁剪同一套公式，且会和栈顶父级求交。
            Renderer.pushScissorScreen(
                    Math.round(clickGui.toScaledX(posX)),
                    Math.round(clickGui.toScaledY(posY + 20.0f)),
                    Math.max(1, Math.round(120.0f * guiScale)),
                    Math.max(1, Math.round(settingsRegionHeight * guiScale)));
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
            Renderer.popScissor();
        }
    }

    // ------------------------------------------------------------------
    // 几何查询 + 动作（供 GuiInputRouter 调用）
    // ------------------------------------------------------------------

    @Override
    public boolean contains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY, 120.0f, totalHeight);
    }

    /** 命中查询：整行（标题行 + 展开的设置区）。 */
    public boolean isWithinBounds(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY, 120.0f, totalHeight);
    }

    /** 标题行命中（左键开关 / 右键展开设置）。 */
    public boolean titleContains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY, 120.0f, 20.0f);
    }

    /** 展开的设置区命中（标题行以下）。 */
    public boolean settingsAreaContains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY + 20.0f, 120.0f, totalHeight - 20.0f);
    }

    /** 左键点击标题行：切换模块开关。 */
    public void toggleModule() {
        module.setEnabled(!module.isEnabled());
    }

    /** 右键点击标题行：展开 / 收起设置区。 */
    public void toggleExpanded() {
        isExpanded = !isExpanded;
    }

    public BindElement getBindElement() {
        return bindElement;
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
