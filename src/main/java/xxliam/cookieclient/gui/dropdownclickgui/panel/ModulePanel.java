package xxliam.cookieclient.gui.dropdownclickgui.panel;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.Component;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownClickGui;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownRender;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.PropertyProvider;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;
import xxliam.cookieclient.utils.render.ThemeHelper;

/**
 * 模块行：左键开关 / 右键展开属性（按住 TAB 显示键名）。
 * <p>
 * 移植自 OpenOpal {@code wtf.opal.client.screen.click.dropdown.panel.ModulePanel}，
 * 视觉参数照搬：行底 0xff1e1e2d@70%、启用渐变叠层主题双色（0.4 目标强度，水平渐变）、
 * 底行下圆角 5；文字 productsans bold/medium 8、TAB 键名 medium 7；hover/toggle 动画
 * DECELERATE(150ms) = cookie EASE_OUT_QUAD，展开 DECELERATE(125ms)。
 */
public class ModulePanel extends Component {

    private final Module module;
    private final PropertyProvider propertyProvider;

    private SmoothAnimationTimer hoverAnim;
    private SmoothAnimationTimer toggleAnim;
    private final SmoothAnimationTimer expandAnim = new SmoothAnimationTimer();

    private boolean lastModule;
    private boolean expanded;

    public ModulePanel(final Module module) {
        this.module = module;
        this.propertyProvider = new PropertyProvider(module, this::isExpandedAnimation);
    }

    private boolean isExpandedAnimation() {
        return expanded || expandAnim.getValueF() > 0.0f;
    }

    @Override
    public void init() {
        propertyProvider.init();
        hoverAnim = null;
        toggleAnim = null;
        expanded = false;
        expandAnim.setCurrentValue(0.0);
    }

    @Override
    public void close() {
        propertyProvider.close();
    }

    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        handleAnimations(mouseX, mouseY);
        if (alpha <= 0.0f) {
            return;
        }

        final int baseColor = 0xff1e1e2d;
        final CustomFont font = module.isEnabled() ? FontStore.PRODUCTSANS_BOLD_8 : FontStore.PRODUCTSANS_MEDIUM_8;
        int[] theme = ThemeHelper.getThemeColors();

        if (!lastModule) {
            Renderer.drawScreenBlur(guiGraphics.pose(), x, y, width, height, 2.5f, 2.5f);
            Renderer.drawRect(guiGraphics.pose(), x, y, width, height,
                    ColorUtil.applyOpacity(baseColor, 0.7f * alpha));
            Renderer.drawRoundedRectGradient(guiGraphics.pose(), x, y, width, height, 0.0f,
                    ColorUtil.applyOpacity(theme[0], toggleAnim.getValueF() * alpha),
                    ColorUtil.applyOpacity(theme[1], toggleAnim.getValueF() * alpha), 0.0f);
        } else {
            Renderer.drawScreenBlur(guiGraphics.pose(), x, y, width, height, 5.0f, 2.5f);
            Renderer.drawRoundedRect(guiGraphics.pose(), x, y, width, height, 0.0f, 0.0f, 5.0f, 5.0f,
                    ColorUtil.applyOpacity(baseColor, 0.7f * alpha));
            Renderer.drawRoundedRectGradient(guiGraphics.pose(), x, y, width, height,
                    0.0f, 0.0f, 5.0f, 5.0f,
                    ColorUtil.applyOpacity(theme[0], toggleAnim.getValueF() * alpha),
                    ColorUtil.applyOpacity(theme[1], toggleAnim.getValueF() * alpha), 0.0f);
        }

        // hover 变深（zen 的 hover 高亮在 opal 侧的对应物）：模块行上叠一层半透明黑，
        // 启用（主题渐变）/ 未启用（深灰底）都压暗，层随 150ms hoverAnim 过渡；
        // 范围只盖交互行（不含展开的属性行区域），形状跟随末行底部圆角。
        float hoverAmount = hoverAnim.getValueF();
        if (hoverAmount > 0.0f) {
            float headerH = height - (isExpanded() ? propertyProvider.getExtraHeight() : 0.0f);
            int hoverColor = ColorUtil.applyOpacity(0xFF000000, 0.4f * hoverAmount * alpha);
            if (!lastModule) {
                Renderer.drawRect(guiGraphics.pose(), x, y, width, headerH, hoverColor);
            } else {
                Renderer.drawRoundedRect(guiGraphics.pose(), x, y, width, headerH, 0.0f, 0.0f, 5.0f, 5.0f, hoverColor);
            }
        }

        Renderer.pushScissor(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
        final int textColor = module.isEnabled() ? ColorUtil.withAlpha(-1, alpha)
                : ColorUtil.withAlpha(ColorUtil.darker(-1, 0.2f), alpha);

        // 模块名：NVG baseline y+12.5（字号 8）
        DropdownRender.baseline(guiGraphics, font, module.getName(), x + 6.0f, y + 12.5f, textColor);

        if (propertyProvider.isHasProperties() && !DropdownClickGui.displayingBinds) {
            // 展开箭头：旋转 180×expand（opal materialicons-regular 12，中心旋转）
            final String expandIcon = "\ue5cf";
            final float iconSize = 12.0f;
            float iconW = FontStore.MATERIALICONS_12.getStringWidth(expandIcon);
            RenderHelper.pushRotateAround(guiGraphics.pose(),
                    x + width - 17.0f + iconW / 2.0f, y + 4.0f + iconSize / 2.0f,
                    expandAnim.getValueF() * 180.0f);
            DropdownRender.iconCentered(guiGraphics, FontStore.MATERIALICONS_12, expandIcon,
                    x + width - 17.0f + iconW / 2.0f, y + 4.0f + iconSize / 2.0f,
                    ColorUtil.withAlpha(textColor, (0.8f - 0.3f * expandAnim.getValueF()) * alpha));
            RenderHelper.popPose(guiGraphics.pose());
        }

        // 键名（TAB 显示）
        String keyString = null;
        if (DropdownClickGui.displayingBinds && module.getKeyBind() != 0) {
            keyString = "[" + DropdownRender.keyName(module.getKeyBind()) + "]";
        }
        if (keyString != null) {
            CustomFont keyFont = FontStore.PRODUCTSANS_MEDIUM_7;
            float kw = keyFont.getStringWidth(keyString);
            DropdownRender.baseline(guiGraphics, keyFont, keyString,
                    x + width - kw - 5.0f, y + 12.0f, ColorUtil.withAlpha(-1, alpha));
        }

        if (expandAnim.isDone() && !isExpanded()) {
            Renderer.popScissor();
            return;
        }

        propertyProvider.setX(x);
        propertyProvider.setY(y + 20.0f);
        propertyProvider.setWidth(width);
        propertyProvider.render(guiGraphics, mouseX, mouseY, delta, alpha * expandAnim.getValueF());
        Renderer.popScissor();
    }

    private void handleAnimations(final float mouseX, final float mouseY) {
        float hoverFactor = isHovering(x, y, width,
                height - (isExpanded() ? propertyProvider.getExtraHeight() : 0.0f), mouseX, mouseY) ? 0.7f : 0.0f;
        if (hoverAnim == null) {
            hoverAnim = new SmoothAnimationTimer();
            hoverAnim.setCurrentValue(hoverFactor);
        } else {
            hoverAnim.animate(hoverFactor, 0.15, Easings.EASE_OUT_QUAD);
            hoverAnim.tick();
        }
        float toggledFactor = module.isEnabled() ? 0.4f : 0.0f;
        if (toggleAnim == null) {
            toggleAnim = new SmoothAnimationTimer();
            toggleAnim.setCurrentValue(toggledFactor);
        } else {
            toggleAnim.animate(toggledFactor, 0.15, Easings.EASE_OUT_QUAD);
            toggleAnim.tick();
        }
        // 展开动画由 CategoryPanel 的 getTotalHeight / 行高计算统一驱动（opal 只在分类面板
        // run 一次），此处不重复 tick，避免每帧双倍推进
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(x, y, width, height - (isExpanded() ? propertyProvider.getExtraHeight() : 0.0f), mouseX, mouseY)) {
            if (button == 0) {
                module.toggle();
            } else if (button == 1) {
                if (propertyProvider.isHasProperties()) {
                    expanded = !expanded;
                }
            }
        }
        propertyProvider.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void keyPressed(int keyCode) {
        propertyProvider.keyPressed(keyCode);
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        propertyProvider.charTyped(chr, modifiers);
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        propertyProvider.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        propertyProvider.mouseReleased(mouseX, mouseY, button);
    }

    public float getAddedHeight() {
        return propertyProvider.getExtraHeight();
    }

    public SmoothAnimationTimer getExpandAnimation() {
        return expandAnim;
    }

    public Module getModule() {
        return module;
    }

    public void setDimensions(float x, float y, float width, float height) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
    }

    public void setLastModule(final boolean lastModule) {
        this.lastModule = lastModule;
    }

    private static boolean isHovering(float x, float y, float w, float h, double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
