package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.ColorSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.awt.Color;
import java.util.Locale;

/**
 * 颜色设置控件：首行显示名称 + HEX + 当前色块；左键点击展开 <b>HSV 色谱取色盘</b>。
 * <p>
 * 展开区照搬 opal {@code ColorPropertyComponent}（原 zen 的 R/G/B/A 四条通道滑条已删除）：
 * 65×50 拾色器（底色 = 纯色相，上叠「白 左→右淡出」与「黑 上→下加深」两层线性渐变）+
 * 5px 间距 + 8×50 的 18 段竖向彩虹色相条；拾色器左上角为 S=0/B=1，横向为饱和度、纵向为亮度；
 * 两者各有一个白色指针标记当前值。
 * <p>
 * 拖动交互：拖动状态（拾色器 / 色相条）由 {@code input.ColorPickerInputHandler} 持有，
 * 回写经 {@code GuiInputRouter.mouseDragged} 驱动 —— 本类只暴露
 * {@link #applyPicker(double, double)} / {@link #applyHue(double)} 两个动作方法。
 * <p>
 * 与 opal 的差异：①首行保留 zen 自己的「名称 + HEX + 色块」三件套（opal 首行只有名称 + 色块）；
 * ②展开时长取 0.2s EASE_OUT_POW2，与模块展开区背景（{@code ModuleElement} 的 settingsHeightTimer）
 * 同帧同曲线（opal 用的是 125ms DECELERATE）；
 * ③<b>不提供 alpha 调整</b>（opal 亦无），改色只写 RGB，原 alpha 原样保留。
 */
public class ColorSettingElement extends SettingElement<ColorSetting> {

    private static final float ROW_HEIGHT = 18.0f;
    /** 展开区：拾色器与色相条的尺寸 / 内缩（照搬 opal ColorPropertyComponent 的 5 / 65 / 50 / 5 / 8）。 */
    private static final float PICKER_X = 5.0f;
    private static final float PICKER_W = 65.0f;
    private static final float PICKER_H = 50.0f;
    private static final float HUE_GAP = 5.0f;
    private static final float HUE_W = 8.0f;
    /** 色相条分段数（照搬 opal rainbowRect 的 18 段渐变）。 */
    private static final int HUE_SEGMENTS = 18;

    private final SmoothAnimationTimer expandTimer = new SmoothAnimationTimer();
    private boolean expanded;
    private boolean isHovered;
    private boolean isTruncated;

    public ColorSettingElement(CategoryPanel parentPanel, ColorSetting setting) {
        super(parentPanel, setting);
    }

    private int color() {
        return setting.getColor();
    }

    private void setColor(int color) {
        setting.setValue(color);
    }

    @Override
    public float getHeight() {
        // 必须是「目标高度」（立即返回），不能返回动画中的值：
        // ModuleElement 把本值累加成 settingsTotalHeight，再喂给 0.2s 的背景展开计时器；
        // 若这里返回动画值，目标每帧都在变 → SmoothAnimationTimer.animate() 每帧重置 startTime
        // （见其注释：target 变化才重置）→ 背景要等本控件动画彻底结束才开始长，色谱就会「先铺开」。
        return ROW_HEIGHT + (expanded ? PICKER_H : 0.0f);
    }

    @Override
    public float getAnimatedHeight() {
        // 动画中的高度：仅用于它下方元素（Bind 行）的堆叠定位。
        return ROW_HEIGHT + PICKER_H * expandTimer.getValueF();
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        isHovered = CursorUtil.isInBounds(mouseX, mouseY, x, y, 120.0f, getHeight());
        visibilityTimer.animate(setting.getVisibility().displayable() ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        visibilityTimer.tick();
        // 展开节奏与模块展开区背景（ModuleElement 的 settingsHeightTimer：0.2s EASE_OUT_POW2）完全一致，
        // 两者同一帧启动、同曲线 → 色谱的铺开与灰底长高严丝合缝。勿单独改成别的时长/缓动。
        expandTimer.animate(expanded ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        expandTimer.tick();
        alpha *= visibilityTimer.getValueF();
        if (Mth.equal(alpha, 0.0f)) {
            return;
        }

        // ---- 首行：名称 + HEX + 色块 ----
        String name = setting.getName();
        if (FontStore.AXIFORMA_REGULAR_14.getStringWidth(name) > 66.0f) {
            name = name.substring(0, Math.min(9, name.length())) + "...";
            isTruncated = true;
        }
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + 6.0f,
                y + (ROW_HEIGHT - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f, ThemeHelper.foreground(alpha * 0.8f));

        float hexWidth = FontStore.AXIFORMA_BOLD_13.getStringWidth(hex());
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, hex(), x + 120.0f - hexWidth - 6.0f - 18.0f,
                y + (ROW_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f, ThemeHelper.foreground(alpha * 0.85f));

        // 色块（不透明混合预览：RGB 覆盖在面板底色上按 alpha 混合）
        float swatchX = x + 120.0f - 18.0f - 5.0f;
        float swatchY = y + (ROW_HEIGHT - 12.0f) / 2.0f;
        Renderer.drawRoundedRect(poseStack, swatchX, swatchY, 12.0f, 12.0f, 2.0f, ColorUtil.withAlpha(blendOnBg(color()), alpha));

        // ---- 展开的 HSV 色谱 ----
        if (expandTimer.getValueF() > 0.01f) {
            drawPicker(clickGui, poseStack, alpha);
        }

        if (isHovered && isTruncated) {
            parentPanel.setHoveredSettingElement(this);
            parentPanel.setTooltipText(setting.getName());
            parentPanel.setShowTooltip(true);
        } else if (parentPanel.getHoveredSettingElement() == this) {
            parentPanel.setShowTooltip(false);
            parentPanel.setHoveredSettingElement(null);
        }
    }

    /** 绘制展开区：拾色器 + 色相条 + 两个指针（opal ColorPropertyComponent.render 的展开分支）。 */
    private void drawPicker(NewClickGui clickGui, PoseStack poseStack, float alpha) {
        float xPos = x + PICKER_X;
        float yPos = y + ROW_HEIGHT;
        float[] hsb = currentHsb();
        float hue = hsb[0];

        // 组件级裁剪：展开动画期间拾色器不得越出本行（对应 opal 的 NVGRenderer.scissor(x, y, width, height)）。
        // 裁剪高度取**动画中的高度**（不是 getHeight() 的目标值），色谱才会随 0.2s 曲线逐步铺开。
        // scissor 不受 Pose 影响，须换算到整体缩放后的屏幕坐标——与 CategoryPanel 的 pushScissorScreen 同一套公式。
        float guiScale = NewClickGui.scale();
        Renderer.pushScissorScreen(
                Math.round(clickGui.toScaledX(x)),
                Math.round(clickGui.toScaledY(y)),
                Math.max(1, Math.round(120.0f * guiScale)),
                Math.max(1, Math.round(getAnimatedHeight() * guiScale)));

        // 拾色器底：纯色相 + 白（左→右淡出）+ 黑（上→下加深）
        Renderer.drawRect(poseStack, xPos, yPos, PICKER_W, PICKER_H,
                ColorUtil.withAlpha(Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000, alpha));
        Renderer.drawRoundedRectGradient(poseStack, xPos, yPos, PICKER_W, PICKER_H, 0.0f,
                ColorUtil.withAlpha(0xFFFFFFFF, alpha), ColorUtil.withAlpha(0xFFFFFFFF, 0.0f), 0.0f);
        Renderer.drawRoundedRectGradient(poseStack, xPos, yPos, PICKER_W, PICKER_H, 0.0f,
                ColorUtil.withAlpha(0xFF000000, 0.0f), ColorUtil.withAlpha(0xFF000000, alpha), 90.0f);

        // 拾色器指针（当前 饱和度 / 亮度）
        float px = xPos + hsb[1] * PICKER_W;
        float py = yPos + (1.0f - hsb[2]) * PICKER_H;
        Renderer.drawRoundedRect(poseStack, px - 2.0f, py - 2.0f, 4.0f, 4.0f, 2.0f, ColorUtil.withAlpha(-1, alpha));

        // 色相条（竖向彩虹，18 段渐变拼接）
        float barX = xPos + PICKER_W + HUE_GAP;
        float segH = PICKER_H / HUE_SEGMENTS;
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            float t0 = (float) i / HUE_SEGMENTS;
            float t1 = (float) (i + 1) / HUE_SEGMENTS;
            int c0 = Color.HSBtoRGB(t0, 1.0f, 1.0f) | 0xFF000000;
            int c1 = Color.HSBtoRGB(t1, 1.0f, 1.0f) | 0xFF000000;
            Renderer.drawRoundedRectGradient(poseStack, barX, yPos + t0 * PICKER_H, HUE_W, segH + 0.5f, 0.0f,
                    ColorUtil.withAlpha(c0, alpha), ColorUtil.withAlpha(c1, alpha), 90.0f);
        }
        // 色相指针（横跨色相条的白色细条）
        float hueY = yPos + hue * PICKER_H;
        Renderer.drawRoundedRect(poseStack, barX - 1.0f, hueY - 1.0f, HUE_W + 2.0f, 2.0f, 1.0f,
                ColorUtil.withAlpha(-1, alpha));

        Renderer.popScissor();
    }

    // ------------------------------------------------------------------
    // 几何查询 + 动作（供 GuiInputRouter / ColorPickerInputHandler 调用）
    // ------------------------------------------------------------------

    /** 命中查询：首行（名称 + HEX + 色块）—— 左键点击切换展开。 */
    public boolean rowContains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, x, y, 120.0f, ROW_HEIGHT);
    }

    /** 命中查询：展开区的拾色器。 */
    public boolean pickerContains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, x + PICKER_X, y + ROW_HEIGHT, PICKER_W, PICKER_H);
    }

    /** 命中查询：展开区的竖向色相条。 */
    public boolean hueContains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY,
                x + PICKER_X + PICKER_W + HUE_GAP, y + ROW_HEIGHT, HUE_W, PICKER_H);
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** 展开 / 收起色谱。 */
    public void toggleExpanded() {
        expanded = !expanded;
    }

    /** 按鼠标位置回写饱和度 / 亮度（拾色器拖动）。 */
    public void applyPicker(double mouseX, double mouseY) {
        float xPos = x + PICKER_X;
        float yPos = y + ROW_HEIGHT;
        float[] hsb = currentHsb();
        setFromHsb(hsb[0],
                Mth.clamp((float) (mouseX - xPos) / PICKER_W, 0.0f, 1.0f),
                Mth.clamp(1.0f - (float) (mouseY - yPos) / PICKER_H, 0.0f, 1.0f));
    }

    /** 按鼠标 y 回写色相（色相条拖动）。 */
    public void applyHue(double mouseY) {
        float yPos = y + ROW_HEIGHT;
        float[] hsb = currentHsb();
        setFromHsb(Mth.clamp((float) (mouseY - yPos) / PICKER_H, 0.0f, 1.0f), hsb[1], hsb[2]);
    }

    /**
     * 写入 HSB：只替换 RGB 三通道，<b>原 alpha 原样保留</b>（照搬 opal 的 setValue 语义，
     * 但 cookie 的 ColorSetting 是 ARGB，多出的 alpha 必须自己兜住）。
     */
    private void setFromHsb(float hue, float saturation, float brightness) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        setColor((color() & 0xFF000000) | rgb);
    }

    /** 当前色的 HSB（H/S/B 均为 0~1）。 */
    private float[] currentHsb() {
        int c = color();
        return Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
    }

    private String hex() {
        int c = color();
        return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
    }

    /** 把当前 ARGB 混到面板底色上（底色随明暗主题：Dark 23,23,23 / Light 纯白），得到不透明预览色。 */
    private static int blendOnBg(int color) {
        int a = (color >>> 24) & 0xFF;
        if (a >= 255) {
            return color | 0xFF000000;
        }
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float t = a / 255.0f;
        int bgRgb = ThemeHelper.surfaceRgb();
        int br = (bgRgb >> 16) & 0xFF;
        int bg = (bgRgb >> 8) & 0xFF;
        int bb = bgRgb & 0xFF;
        return 0xFF000000
                | ((Math.round(r * t + br * (1.0f - t)) & 0xFF) << 16)
                | ((Math.round(g * t + bg * (1.0f - t)) & 0xFF) << 8)
                | (Math.round(b * t + bb * (1.0f - t)) & 0xFF);
    }
}
