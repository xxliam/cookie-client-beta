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
 * 模块行：左键开关 / 右键展开属性 / 中键进入绑定监听（按住 TAB 显示键名）。
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
    private boolean selectingBind;

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
        selectingBind = false;
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

        Renderer.pushScissor(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
        final int textColor = module.isEnabled() ? ColorUtil.withAlpha(-1, alpha)
                : ColorUtil.withAlpha(ColorUtil.darker(-1, 0.2f), alpha);

        // 模块名：NVG baseline y+12.5（字号 8）
        DropdownRender.baseline(guiGraphics, font, module.getName(), x + 6.0f, y + 12.5f, textColor);

        if (propertyProvider.isHasProperties() && !selectingBind && !DropdownClickGui.displayingBinds) {
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

        // 键名（TAB 显示 / 绑定监听中）
        String keyString = null;
        if (selectingBind) {
            keyString = "[...]";
        } else if (DropdownClickGui.displayingBinds && module.getKeyBind() != 0) {
            keyString = "[" + keyName(module.getKeyBind()) + "]";
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
        if (selectingBind) {
            // 绑定监听中：鼠标侧键(3~7)可绑为鼠标键；普通键视为取消监听（避免误写 0=未绑定）
            if (button >= 3 && button <= 7) {
                module.setKeyBind(button);
            }
            selectingBind = DropdownClickGui.selectingBind = false;
            return;
        }
        if (isHovering(x, y, width, height - (isExpanded() ? propertyProvider.getExtraHeight() : 0.0f), mouseX, mouseY)) {
            if (button == 0) {
                module.toggle();
            } else if (button == 1) {
                if (propertyProvider.isHasProperties()) {
                    expanded = !expanded;
                }
            } else if (button == 2) {
                selectingBind = DropdownClickGui.selectingBind = true;
            }
        }
        propertyProvider.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void keyPressed(int keyCode) {
        if (selectingBind) {
            // ESC 取消；其余键绑定（0=未绑定语义下键盘码 ≥32 才有意义，但原样存储，BindElement 同样过滤）
            if (keyCode != 256) {
                module.setKeyBind(keyCode);
            }
            selectingBind = DropdownClickGui.selectingBind = false;
            return;
        }
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

    /** 键码显示名（与 BindElement 的 Mouse4~8 / GLFW 命名同规则，仅取短名）。 */
    private static String keyName(int key) {
        if (key >= 3 && key <= 7) {
            return "Mouse " + (key + 1);
        }
        String glfwName = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0);
        if (glfwName != null && !glfwName.isEmpty()) {
            return glfwName.toUpperCase();
        }
        return switch (key) {
            case 340 -> "L_SHIFT";
            case 344 -> "R_SHIFT";
            case 341 -> "L_CTRL";
            case 345 -> "R_CTRL";
            case 342 -> "L_ALT";
            case 346 -> "R_ALT";
            case 258 -> "TAB";
            case 257 -> "ENTER";
            case 259 -> "BACKSPACE";
            case 256 -> "ESC";
            default -> "KEY_" + key;
        };
    }
}
