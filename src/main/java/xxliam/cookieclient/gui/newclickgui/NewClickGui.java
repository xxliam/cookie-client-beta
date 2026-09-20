package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.newclickgui.input.GuiInputRouter;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.impl.render.ClickGui;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI：按分类横排的面板，含打开 / 关闭缩放动画。
 * <p>
 * 整体等比缩放系数为固定值（{@link #scale()} = {@link ClickGui#ZEN_SCALE}，0.96）：
 * 水平以屏幕中心为轴、垂直以面板标题行 {@link #GUI_ANCHOR_Y} 为轴——标题行贴原位、
 * 模块区向下等比收拢。
 * <p>
 * 右下角常驻一个圆形「E」按钮（屏幕坐标、不参与整体缩放）：按下后 {@link #hidden} 折叠
 * 面板（不是关闭——Screen 保留、按钮保留），再按一次展开。面板折叠/展开与按钮显隐动画均与
 * clickgui 的开关动画同曲线（BACK_OUT：收起 0.22s / 展开 0.32s）；按钮阴影与 clickgui 面板
 * shadow 同款（黑色外扩 {@code BTN_SHADOW}=10、soft=10、alpha 80）。按钮直径 26（适中、不喧宾夺主），
 * E 居中按 {@link CustomFont#getGlyphRenderedBounds(char)} 的图谱真实墨迹计算，与上屏像素完全对齐。
 * <p>
 * 渲染与事件两级统一缩放：渲染走 Pose 矩阵，鼠标命中先经 {@link #toLocalX}/{@link #toLocalY}
 * 反算回未缩放空间；scissor 不受 Pose 影响，由面板用 {@link #toScaledX}/{@link #toScaledY}
 * 换算到缩放后屏幕坐标。
 * <p>
 * <b>渲染与交互分离</b>：本类只负责绘制与坐标换算（另加右下角按钮的几何），所有鼠标 / 键盘
 * 事件一律委托给 {@link GuiInputRouter}（含面板 / 模块 / 设置项命中与全部拖动状态）。
 */
public class NewClickGui extends Screen {

    /** 垂直缩放锚点：面板标题栏所在 y（面板从该行向下生长，缩放后顶部仍贴原高度）。 */
    public static final float GUI_ANCHOR_Y = 36.0f;

    /**
     * ClickGUI 整体等比缩放系数：固定 {@link ClickGui#ZEN_SCALE}（= 原 zen 常量 0.80 × 1.2 = 0.96）。
     * <p>
     * 原 opal 风格及与其配套的 {@code Scale} 滑条已于 2026-09-18 随 opal GUI 一并删除，故本值
     * 现在是唯一的 GUI 尺度、不再有「两风格不同基准」的问题。
     */
    public static float scale() {
        return ClickGui.getZenScaleFactor();
    }

    /** GLFW 实时按键状态。 */
    private static boolean isKeyDown(int key) {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), key) == GLFW.GLFW_PRESS;
    }

    /** 右下角圆形按钮直径 / 阴影扩散 / 距屏缘留白（逻辑像素）。 */
    private static final float BTN_DIAMETER = 26.0f;
    private static final float BTN_RADIUS = BTN_DIAMETER / 2.0f;
    private static final float BTN_SHADOW = 10.0f;
    private static final float BTN_MARGIN = 12.0f;

    private static final List<CategoryPanel> CATEGORY_PANELS = new ArrayList<>();
    public static CategoryPanel focusedPanel;

    /**
     * TAB 是否按住（按住时各模块行右侧显示其绑定键名，并暂时让出展开箭头的位置）。
     * <p>
     * TAB 悬浮键名功能自 opal 风格下拉 GUI（已删除）移植，驱动方式相同：每帧在
     * {@link #render} 里按 GLFW 实时按键状态刷新。
     */
    public static boolean displayingBinds;

    private boolean closing;
    /** 折叠态：true=面板隐藏（非关闭，Screen 与按钮保留），false=面板展开。 */
    private boolean hidden;
    private final SmoothAnimationTimer closeAnim = new SmoothAnimationTimer();
    /** 面板折叠/展开动画（1=展开，0=收起；BACK_OUT 0.32/0.22，与 clickgui 开关一致）。 */
    private final SmoothAnimationTimer hideAnim = new SmoothAnimationTimer();
    /** 右下按钮显隐动画（1=显示，0=隐藏；BACK_OUT 0.32/0.22，与 clickgui 开关一致）。 */
    private final SmoothAnimationTimer btnAnim = new SmoothAnimationTimer();

    /** 全部鼠标 / 键盘交互的唯一入口（命中路由 + 各类拖动状态）。 */
    private final GuiInputRouter input = new GuiInputRouter(this);

    public NewClickGui() {
        super(Component.literal("Cookie Client ClickGUI"));
        hideAnim.setCurrentValue(1.0); // 打开即展开，hideAnim 不额外参与首次打开淡入
    }

    @Override
    protected void init() {
        focusedPanel = CATEGORY_PANELS.get(0);
        // 面板横排：每个宽 120、间距 8，整体水平居中（逻辑布局，未缩放）。
        // 注意别再把总宽写死 —— 原先硬编码「7 个面板 = 888」并起点取 width/2 - 444，
        // 一旦新增分类（如 Config）整排就会偏右。这里按实际面板数推导，7 个时结果与原来逐值相同。
        int panelCount = CATEGORY_PANELS.size();
        float totalWidth = panelCount * 120.0f + Math.max(0, panelCount - 1) * 8.0f;
        float panelX = (float) this.width / 2.0f - totalWidth / 2.0f;
        for (CategoryPanel panel : CATEGORY_PANELS) {
            panel.setX(panelX);
            panel.setY(GUI_ANCHOR_Y);
            panelX += 128.0f;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        displayingBinds = isKeyDown(GLFW.GLFW_KEY_TAB);
        closeAnim.animate(closing ? 0.0 : 1.0, 0.2, Easings.EASE_OUT_POW2);
        closeAnim.tick();
        float closeProgress = closeAnim.getValueF();
        if (Mth.equal(closeProgress, 0.0f) && closing) {
            closing = false;
            super.onClose(); // Screen.onClose 默认 = setScreen(null)，此刻 mc.screen 已清空
            for (CategoryPanel panel : CATEGORY_PANELS) {
                panel.reset();
            }
            ClickGui.onGuiClosed(); // 复位 ClickGui 模块（enabled 仅 GUI 打开期间为 true）
            CookieClient.CONFIG_MANAGER.save(); // 自动保存：GUI 关闭时持久化本次会话的开关/设置/绑定
            return;
        }
        hideAnim.animate(hidden ? 0.0 : 1.0, hidden ? 0.22 : 0.32, Easings.BACK_OUT);
        hideAnim.tick();
        float panelAlpha = closeProgress * (float) hideAnim.getValueF();

        // 面板层：整体 GUI_SCALE 等比缩放；hidden 折叠/展开期间 alpha 与面板自身 scaleTimer 同步过渡
        if (panelAlpha > 0.001f) {
            float centerX = this.width / 2.0f;
            float guiScale = scale();
            RenderHelper.pushScaleAround(guiGraphics.pose(), centerX, GUI_ANCHOR_Y, guiScale);
            int localMouseX = (int) ((mouseX - centerX) / guiScale + centerX);
            int localMouseY = (int) ((mouseY - GUI_ANCHOR_Y) / guiScale + GUI_ANCHOR_Y);
            for (CategoryPanel panel : CATEGORY_PANELS) {
                panel.render(this, guiGraphics, guiGraphics.pose(), localMouseX, localMouseY, panelAlpha, delta);
            }
            RenderHelper.popPose(guiGraphics.pose());
        }

        // 右下角圆形按钮（屏幕坐标、不参与 GUI_SCALE；alpha 随 GUI 整体开关 closeProgress 淡入淡出）
        btnAnim.animate(closing ? 0.0 : 1.0, closing ? 0.22 : 0.32, Easings.BACK_OUT);
        btnAnim.tick();
        renderToggleButton(guiGraphics, mouseX, mouseY, closeProgress, (float) btnAnim.getValueF());
    }

    @Override
    public void onClose() {
        closing = true;
        input.cancelAll();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return input.keyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return input.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return input.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return input.mouseDragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return input.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean isClosing() {
        return closing;
    }

    /** 面板是否处于折叠态（右下按钮隐藏，非关闭）。 */
    public boolean isHidden() {
        return hidden;
    }

    /** 点击右下按钮：翻转折叠态（拖动与绑定监听的中止由 {@link GuiInputRouter} 负责）。 */
    public void toggleHidden() {
        hidden = !hidden;
    }

    /** 面板列表（渲染遍历 + 输入路由共用）。 */
    public static List<CategoryPanel> getCategoryPanels() {
        return CATEGORY_PANELS;
    }

    // ---------------------------------------------------------------------
    // 右下角圆形「E」按钮
    // ---------------------------------------------------------------------

    private float btnCenterX() {
        return this.width - BTN_MARGIN - BTN_RADIUS;
    }

    private float btnCenterY() {
        return this.height - BTN_MARGIN - BTN_RADIUS;
    }

    /** 命中检测：按钮圆内（略放宽容差）。用屏幕坐标判定，与按钮绘制一致不经过 GUI_SCALE。 */
    public boolean isToggleButtonHit(double mouseX, double mouseY) {
        float dx = (float) (mouseX - btnCenterX());
        float dy = (float) (mouseY - btnCenterY());
        float hit = BTN_RADIUS + 4.0f;
        return dx * dx + dy * dy <= hit * hit;
    }

    /**
     * 绘制按钮：黑色圆 + clickgui 同款软阴影（外扩 BTN_SHADOW、soft=BTN_SHADOW、alpha 80），
     * 中心白色 E 按图谱真实墨迹居中；scaleAmount 走与 clickgui 相同的 BACK_OUT 展开/收起曲线
     * （0.4 + 0.6×value，允许超调，与面板入场动画一致）。
     */
    private void renderToggleButton(GuiGraphics guiGraphics, int mouseX, int mouseY, float alpha, float scaleAmount) {
        if (scaleAmount <= 0.0f) {
            return;
        }
        float cx = btnCenterX();
        float cy = btnCenterY();
        float half = BTN_DIAMETER / 2.0f;
        RenderHelper.pushScaleAround(guiGraphics.pose(), cx, cy, 0.4f + 0.6f * scaleAmount);
        PoseStack pose = guiGraphics.pose();

        // 阴影（与 clickgui 面板 drawRoundedRect 阴影同款：更大一圈 + soft=扩散量 + alpha 80）
        float sh = BTN_SHADOW;
        Renderer.drawRoundedRect(pose, cx - half - sh, cy - half - sh,
                BTN_DIAMETER + sh * 2.0f, BTN_DIAMETER + sh * 2.0f,
                (BTN_DIAMETER + sh * 2.0f) / 2.0f, sh,
                ColorUtil.fromARGB(0, 0, 0, (int) (80.0f * alpha)));

        // 本体：纯黑圆
        Renderer.drawRoundedRect(pose, cx - half, cy - half, BTN_DIAMETER, BTN_DIAMETER,
                BTN_RADIUS, 1.0f, ColorUtil.withAlpha(0xFF000000, alpha));

        // hover 微高亮（半透明白叠层，强化可点感）
        if (isToggleButtonHit(mouseX, mouseY)) {
            Renderer.drawRoundedRect(pose, cx - half, cy - half, BTN_DIAMETER, BTN_DIAMETER,
                    BTN_RADIUS, 1.0f, ColorUtil.fromARGB(255, 255, 255, (int) (14.0f * alpha)));
        }

        // 中心 E：按图谱真实墨迹（getGlyphRenderedBounds：扫描 atlas 像素，与上屏后最终像素完全一致）
        // 在按钮圆心居中——比 AWT 几何 ink 准确，避免 1~2 像素的几何↔像素漂移。
        CustomFont font = FontStore.AXIFORMA_EXTRABOLD_18;
        String glyph = "E";
        CustomFont.GlyphVisualBounds ink = font.getGlyphRenderedBounds('E');
        float asc = font.getFontMetrics().getAscent() / (float) font.getScale();
        int color = ColorUtil.withAlpha(0xFFFFFFFF, alpha);
        if (ink.width() <= 0.0f || ink.height() <= 0.0f) {
            font.drawString(pose, glyph,
                    cx - font.getStringWidth(glyph) / 2.0f,
                    cy + 1.0f - asc / 2.0f, color);
        } else {
            font.drawString(pose, glyph,
                    cx - ink.x() - ink.width() / 2.0f,
                    cy + 1.0f - asc - ink.y() - ink.height() / 2.0f, color);
        }
        RenderHelper.popPose(pose);
    }

    // ---------------------------------------------------------------------
    // 常量缩放的坐标映射（与 render 的 pushScaleAround 保持同一套公式）
    // ---------------------------------------------------------------------

    /** 屏幕 x → 未缩放空间 x（鼠标命中反算）。 */
    public double toLocalX(double screenX) {
        float centerX = this.width / 2.0f;
        return (screenX - centerX) / scale() + centerX;
    }

    /** 屏幕 y → 未缩放空间 y（鼠标命中反算）。 */
    public double toLocalY(double screenY) {
        return (screenY - GUI_ANCHOR_Y) / scale() + GUI_ANCHOR_Y;
    }

    /** 面板局部 x → 缩放后屏幕 x（scissor 等不受 Pose 影响的绘制需要）。 */
    public float toScaledX(float localX) {
        float centerX = this.width / 2.0f;
        return centerX + (localX - centerX) * scale();
    }

    /** 面板局部 y → 缩放后屏幕 y。 */
    public float toScaledY(float localY) {
        return GUI_ANCHOR_Y + (localY - GUI_ANCHOR_Y) * scale();
    }

    static {
        for (Category category : Category.values()) {
            CATEGORY_PANELS.add(new CategoryPanel(category));
        }
    }
}
