package xxliam.cookieclient.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.newclickgui.BindElement;
import xxliam.cookieclient.gui.newclickgui.CategoryPanel;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.impl.render.hud.ModuleList;
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
 * 整体等比缩放系数固定 {@link #GUI_SCALE}（0.8 = 缩小 20%）：水平以屏幕中心为轴、
 * 垂直以面板标题行 {@link #GUI_ANCHOR_Y} 为轴——标题行贴原位、模块区向下等比收拢。
 * <p>
 * 右下角常驻一个圆形「E」按钮（屏幕坐标、不参与 GUI_SCALE）：按下后 {@link #hidden} 折叠
 * 面板（不是关闭——Screen 保留、按钮保留），再按一次展开。面板折叠/展开与按钮显隐动画均与
 * clickgui 的开关动画同曲线（BACK_OUT：收起 0.22s / 展开 0.32s）；按钮阴影与 clickgui 面板
 * shadow 同款（黑色外扩 {@code BTN_SHADOW}=10、soft=10、alpha 80）。按钮直径 26（适中、不喧宾夺主），
 * E 居中按 {@link CustomFont#getGlyphRenderedBounds(char)} 的图谱真实墨迹计算，与上屏像素完全对齐。
 * <p>
 * 渲染与事件两级统一缩放：渲染走 Pose 矩阵，鼠标命中先经 {@link #toLocalX}/{@link #toLocalY}
 * 反算回未缩放空间；scissor 不受 Pose 影响，由面板用 {@link #toScaledX}/{@link #toScaledY}
 * 换算到缩放后屏幕坐标。
 */
public class NewClickGui extends Screen {

    /** ClickGUI 整体等比缩放系数（0.8 = 缩小 20%）。 */
    public static final float GUI_SCALE = 0.8f;
    /** 垂直缩放锚点：面板标题栏所在 y（面板从该行向下生长，缩放后顶部仍贴原高度）。 */
    public static final float GUI_ANCHOR_Y = 36.0f;

    /** 右下角圆形按钮直径 / 阴影扩散 / 距屏缘留白（逻辑像素）。 */
    private static final float BTN_DIAMETER = 26.0f;
    private static final float BTN_RADIUS = BTN_DIAMETER / 2.0f;
    private static final float BTN_SHADOW = 10.0f;
    private static final float BTN_MARGIN = 12.0f;

    private static final List<CategoryPanel> CATEGORY_PANELS = new ArrayList<>();
    public static CategoryPanel focusedPanel;

    private boolean closing;
    /** 折叠态：true=面板隐藏（非关闭，Screen 与按钮保留），false=面板展开。 */
    private boolean hidden;
    private final SmoothAnimationTimer closeAnim = new SmoothAnimationTimer();
    /** 面板折叠/展开动画（1=展开，0=收起；BACK_OUT 0.32/0.22，与 clickgui 开关一致）。 */
    private final SmoothAnimationTimer hideAnim = new SmoothAnimationTimer();
    /** 右下按钮显隐动画（1=显示，0=隐藏；BACK_OUT 0.32/0.22，与 clickgui 开关一致）。 */
    private final SmoothAnimationTimer btnAnim = new SmoothAnimationTimer();

    // ---- ModuleList 布局编辑（仅 hidden 折叠态）：按住列表包围框拖动整列 ----
    private boolean moduleDragActive;
    private float moduleDragStartX;
    private float moduleDragStartY;
    private float moduleDragBaseOffsetX;
    private float moduleDragBaseOffsetY;

    public NewClickGui() {
        super(Component.literal("Cookie Client ClickGUI"));
        hideAnim.setCurrentValue(1.0); // 打开即展开，hideAnim 不额外参与首次打开淡入
    }

    @Override
    protected void init() {
        focusedPanel = CATEGORY_PANELS.get(0);
        // 7 个面板，每个宽 120、间距 8，总宽 888，起点居中 = width/2 - 444（未缩放逻辑布局）
        float panelX = (float) this.width / 2.0f - 444.0f;
        for (CategoryPanel panel : CATEGORY_PANELS) {
            panel.setX(panelX);
            panel.setY(GUI_ANCHOR_Y);
            panelX += 128.0f;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        closeAnim.animate(closing ? 0.0 : 1.0, 0.2, Easings.EASE_OUT_POW2);
        closeAnim.tick();
        float closeProgress = closeAnim.getValueF();
        if (Mth.equal(closeProgress, 0.0f) && closing) {
            closing = false;
            super.onClose(); // Screen.onClose 默认 = setScreen(null)，此刻 mc.screen 已清空
            for (CategoryPanel panel : CATEGORY_PANELS) {
                panel.reset();
            }
            CookieClient.CONFIG_MANAGER.save(); // 持久化本次 GUI 会话中的开关/设置/绑定
            return;
        }
        hideAnim.animate(hidden ? 0.0 : 1.0, hidden ? 0.22 : 0.32, Easings.BACK_OUT);
        hideAnim.tick();
        float panelAlpha = closeProgress * (float) hideAnim.getValueF();

        // 面板层：整体 GUI_SCALE 等比缩放；hidden 折叠/展开期间 alpha 与面板自身 scaleTimer 同步过渡
        if (panelAlpha > 0.001f) {
            float centerX = this.width / 2.0f;
            RenderHelper.pushScaleAround(guiGraphics.pose(), centerX, GUI_ANCHOR_Y, GUI_SCALE);
            int localMouseX = (int) ((mouseX - centerX) / GUI_SCALE + centerX);
            int localMouseY = (int) ((mouseY - GUI_ANCHOR_Y) / GUI_SCALE + GUI_ANCHOR_Y);
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
        BindElement.clearListening();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 存在监听态的 Bind 按钮时，优先把按键交给它绑定
        BindElement listening = BindElement.getListening();
        if (listening != null) {
            return listening.onKey(keyCode);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右下角圆形按钮：折叠 / 展开面板（任何按键都行，取左键即可）
        if (isButtonHit(mouseX, mouseY)) {
            if (button == 0) {
                toggleHidden();
            }
            return true;
        }
        if (hidden) {
            // 折叠态：左键点在 ModuleList 包围框上开始拖动；其余点击一律吞掉
            if (button == 0 && tryStartModuleDrag(mouseX, mouseY)) {
                return true;
            }
            return true; // 面板已折叠：除按钮外不响应任何点击
        }
        // 存在监听态的 Bind 按钮时，侧键按下直接作为鼠标绑定捕获（与 keyPressed 路由按键对称）
        BindElement listening = BindElement.getListening();
        if (listening != null && listening.onMouse(button)) {
            return true;
        }
        for (CategoryPanel panel : CATEGORY_PANELS) {
            if (panel.mouseClicked(toLocalX(mouseX), toLocalY(mouseY), button)) {
                focusedPanel = panel;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (moduleDragActive) {
            moduleDragActive = false;
            return true;
        }
        if (hidden) {
            return false;
        }
        for (CategoryPanel panel : CATEGORY_PANELS) {
            panel.mouseReleased(toLocalX(mouseX), toLocalY(mouseY), button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // ModuleList 布局拖动：跟随光标并把目标偏移钳制在屏幕内（边框不超出屏幕）。
        // FREE_DRAG=true（自由双向）时沿用下方完整自由移动计算（备用，勿删）；
        // 当前贴边垂直模式（false）仅锁定水平分量 = 拖动基准，列表只能沿屏幕边缘上下移动。
        if (moduleDragActive && ModuleList.INSTANCE != null) {
            float nx = moduleDragBaseOffsetX + (float) (mouseX - moduleDragStartX);
            float ny = moduleDragBaseOffsetY + (float) (mouseY - moduleDragStartY);
            if (!ModuleList.FREE_DRAG) {
                nx = moduleDragBaseOffsetX;
            }
            ModuleList.INSTANCE.setDraggedOffset(nx, ny);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 命中 ModuleList 包围框则进入拖动态（记录抓取基准）。 */
    private boolean tryStartModuleDrag(double mouseX, double mouseY) {
        if (ModuleList.INSTANCE == null || !ModuleList.INSTANCE.isFrameHit(mouseX, mouseY)) {
            return false;
        }
        moduleDragActive = true;
        moduleDragStartX = (float) mouseX;
        moduleDragStartY = (float) mouseY;
        moduleDragBaseOffsetX = ModuleList.INSTANCE.getOffsetX();
        moduleDragBaseOffsetY = ModuleList.INSTANCE.getOffsetY();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (hidden) {
            return false;
        }
        for (CategoryPanel panel : CATEGORY_PANELS) {
            if (panel.mouseScrolled(toLocalX(mouseX), toLocalY(mouseY), scrollDelta)) {
                return true;
            }
        }
        return false;
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

    /** 点击右下按钮：翻转折叠态并中止可能存在的绑定监听 / ModuleList 拖动。 */
    private void toggleHidden() {
        hidden = !hidden;
        moduleDragActive = false;
        BindElement.clearListening();
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
    private boolean isButtonHit(double mouseX, double mouseY) {
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
        if (isButtonHit(mouseX, mouseY)) {
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
        return (screenX - centerX) / GUI_SCALE + centerX;
    }

    /** 屏幕 y → 未缩放空间 y（鼠标命中反算）。 */
    public double toLocalY(double screenY) {
        return (screenY - GUI_ANCHOR_Y) / GUI_SCALE + GUI_ANCHOR_Y;
    }

    /** 面板局部 x → 缩放后屏幕 x（scissor 等不受 Pose 影响的绘制需要）。 */
    public float toScaledX(float localX) {
        float centerX = this.width / 2.0f;
        return centerX + (localX - centerX) * GUI_SCALE;
    }

    /** 面板局部 y → 缩放后屏幕 y。 */
    public float toScaledY(float localY) {
        return GUI_ANCHOR_Y + (localY - GUI_ANCHOR_Y) * GUI_SCALE;
    }

    static {
        for (Category category : Category.values()) {
            CATEGORY_PANELS.add(new CategoryPanel(category));
        }
    }
}
