package xxliam.cookieclient.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.gui.newclickgui.NewClickGui;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easing;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.Arrays;
import java.util.List;

/**
 * DynamicIsland：顶栏居中的「灵动岛」容器，轮换显示一个可见 HUD 元素。
 * <p>
 * 照搬 OpenZen {@code shit.zen.hud.DynamicIsland}：
 * <ul>
 *   <li>元素优先级顺序：Scaffold → EventAlert → AutoPlay → Watermark（常驻）；</li>
 *   <li>动画为<b>点对点曲线</b>：{@link SmoothAnimationTimer} + {@code 0.3s EASE_OUT_POW3}
 *       （原 zen 的 width/height/transition 三个弹簧阻尼不足会过冲弹动，已按需求替换，
 *       时长取 ≈ 原弹簧到达稳定的耗时 ⇒ 速度基本不变、无回弹）；</li>
 *   <li>元素统一按「几何中心锚点」定位（见 {@link #CENTER_ANCHOR_Y}），不再区分 TOP / CENTER；</li>
 *   <li>内容在方形 scissor 内裁剪绘制。</li>
 * </ul>
 * <b>cookie 侧相对 zen 的改动</b>：
 * <ol>
 *   <li><b>外观为胶囊</b>：背景圆角半径取 {@code 高度 / 2}，左右两端即两个半圆；宽度按
 *       {@link #capSideInset} 只让出「圆弧内缩量」而非整个半圆，避免两端过度留白；</li>
 *   <li><b>整体缩小</b>：字号与布局的缩放交给元素自己完成 —— 各元素把 zen 原始视觉字号
 *       与布局常量乘 {@link IslandMetrics#scale()} 后<b>原生栅格化</b>（不再用 pose 等比
 *       缩小，字形因此不会因下采样发虚）；本类只负责在元素尺寸外叠
 *       {@link #PAD_Y} 内边距，并在两端各让出 {@code 圆弧内缩量 + PAD_X_EXTRA}，
 *       pose 缩放恒为 1；</li>
 *   <li><b>布局编辑态</b>（ClickGUI 折叠「E」态，与 ModuleList 同款）：画出
 *       {@code 0xCCFFFFFF} 白色 1px 编辑框 + 屏幕竖直中轴线（白色 30% 虚线），
 *       编辑框可用鼠标抓住、沿中轴线上下拖动（水平锁定在屏幕中央），偏移写入
 *       {@link NumberSetting} 由配置持久化。</li>
 * </ol>
 * 缩放说明：尺寸是固定值 {@link IslandMetrics#SCALE}（元素侧统一经 {@code scale()} 取用）。
 * 字体按 {@code 原始视觉字号 × SCALE} 加载，图谱分辨率与显示尺寸 1:1（仍有 2× 超采样），
 * 锐利度与客户端其它 UI 文本一致。
 */
public class DynamicIsland {

    /** 垂直内边距（屏幕像素）。 */
    private static final float PAD_Y = 4.0f;
    /** 两端在「圆弧内缩量」之外的额外留白（0 = 内容四角正好贴住胶囊圆弧）。 */
    private static final float PAD_X_EXTRA = 0.5f;
    /** 距屏幕上缘的边距（越小越靠上）—— 仅用于推导下面的中心锚点。 */
    private static final float TOP_MARGIN = 14.0f;
    /**
     * 岛的<b>几何中心锚点 Y</b>：所有元素统一按「中心固定」定位，
     * 因此切换元素（或日后调整 {@link IslandMetrics#SCALE}）时岛都不会上下漂移，
     * 尺寸变化始终围绕几何中心。
     * <p>
     * 取值 = {@link #TOP_MARGIN} + WatermarkHud 的岛高一半（其内容高为 zen 原始 32 × 缩放）。
     */
    private static final float CENTER_ANCHOR_Y = TOP_MARGIN
            + (32.0f * IslandMetrics.SCALE + PAD_Y) / 2.0f;

    /** 编辑框颜色（与 ModuleList 同款：白色 80%）。 */
    private static final int EDIT_FRAME_COLOR = 0xCCFFFFFF;
    /** 中轴线颜色：白色 30% 不透明度。 */
    private static final int AXIS_COLOR = 0x4DFFFFFF;
    /** 中轴线虚线节拍（像素）：画 AXIS_DASH，空 AXIS_GAP。 */
    private static final float AXIS_DASH = 4.0f;
    private static final float AXIS_GAP = 4.0f;

    /** 全局实例（仅一个，供 ClickGUI 在折叠态路由拖动）。 */
    public static DynamicIsland INSTANCE;

    /** 选岛器：按优先级返回第一个可见元素。 */
    public static final class ActiveElementSelector {
        private final DynamicIsland owner;

        public ActiveElementSelector(DynamicIsland owner) {
            this.owner = owner;
        }

        public IHudElement visible() {
            for (IHudElement element : owner.elements) {
                if (element.isVisible()) {
                    return element;
                }
            }
            return null;
        }
    }

    private final List<IHudElement> elements = Arrays.asList(
            new ScaffoldHud(),
            new EventAlertHud(),
            new AutoPlayHud(),
            new WatermarkHud());

    private final ActiveElementSelector activeElementSelector = new ActiveElementSelector(this);

    /**
     * 尺寸 / 转场动画：<b>点对点曲线</b>（{@link SmoothAnimationTimer}），不是弹簧。
     * <p>
     * 原 zen 三个弹簧（{@code width(300,1.2,20,170)} / {@code height(300,1.2,20,18)} /
     * {@code transition(250,1,22,1)}）的阻尼都远低于临界值（ζ≈0.53 / 0.70），因此会<b>过冲</b>——
     * 观感就是「弹一下」；`transition` 过冲时 {@code progress > 1} 还会让尺寸插值越过目标值。
     * 现按需求改为无过冲的曲线动画，时长取 ≈ 旧弹簧到达稳定所需的时间，所以**总速度基本不变**、
     * 只是去掉了尾部那几下回弹。
     */
    private static final double ANIM_DURATION = 0.3;
    private static final Easing ANIM_EASING = Easings.EASE_OUT_POW3;

    private final SmoothAnimationTimer widthAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer heightAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer transitionAnim = new SmoothAnimationTimer();
    private IHudElement activeElement = null;
    private IHudElement outgoingElement = null;

    /** 拖动偏移（Y，可持久化；由 Watermark 模块注入）。 */
    private final NumberSetting offsetYSetting;

    // ---- 上一帧的岛几何（供编辑框命中测试 / 拖动钳制；editMode 下每帧刷新） ----
    private float lastX;
    private float lastY;
    private float lastWidth;
    private float lastHeight;
    private float lastOffsetY;

    public DynamicIsland(NumberSetting offsetYSetting) {
        this.offsetYSetting = offsetYSetting;
        INSTANCE = this;
    }

    /**
     * 渲染灵动岛。
     *
     * @param backgroundOpacityPercent 背景不透明度百分比（10~100，来自 Watermark 模块滑条）
     */
    public void render(GuiGraphics guiGraphics, float backgroundOpacityPercent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }

        IHudElement visibleElement = activeElementSelector.visible();
        if (activeElement != visibleElement) {
            outgoingElement = activeElement;
            activeElement = visibleElement;
            transitionAnim.reset(0.0);
            if (outgoingElement == null) {
                // 首次出现：尺寸直接就位，不播转场
                IHudElement.Size size = activeElement.size();
                widthAnim.reset(size.width());
                heightAnim.reset(size.height());
                transitionAnim.reset(1.0);
            }
        }

        IHudElement.Size target = activeElement != null ? activeElement.size() : new IHudElement.Size(170.0f, 18.0f);
        transitionAnim.animate(1.0, ANIM_DURATION, ANIM_EASING);
        transitionAnim.tick();
        // 转场进度 0→1（曲线，无过冲）：既做尺寸插值因子，也做元素淡入系数
        float progress = transitionAnim.getValueF();

        float contentWidth;
        float contentHeight;
        if (outgoingElement != null && progress < 1.0f) {
            // 转场中：尺寸直接在「上一元素尺寸 → 新元素尺寸」之间按 progress 曲线插值（点对点）。
            // 两个尺寸计时器同步到插值结果，转场结束后从当前位置接手，避免再接一次动画产生滞回。
            IHudElement.Size outgoingSize = outgoingElement.size();
            contentWidth = Mth.lerp(progress, outgoingSize.width(), target.width());
            contentHeight = Mth.lerp(progress, outgoingSize.height(), target.height());
            widthAnim.reset(contentWidth);
            heightAnim.reset(contentHeight);
        } else {
            // 常态：元素自身尺寸变化（如服务器名 / 延迟文本变长）时走同一条曲线
            widthAnim.animate(target.width(), ANIM_DURATION, ANIM_EASING);
            heightAnim.animate(target.height(), ANIM_DURATION, ANIM_EASING);
            widthAnim.tick();
            heightAnim.tick();
            contentWidth = widthAnim.getValueF();
            contentHeight = heightAnim.getValueF();
        }
        contentWidth = Math.max(0.0f, contentWidth);
        contentHeight = Math.max(0.0f, contentHeight);
        float islandHeight = Math.max(0.0f, contentHeight + PAD_Y);
        // 胶囊：半径 = 高度一半
        float cornerRadius = islandHeight * 0.5f;
        // 两端只需让出「圆弧在内容区上下边缘处的水平内缩量」——内容四角正好落在圆弧之内。
        // 旧式按 cornerRadius 全额 ×2 会让两端各空出整整一个半圆（本例约 21.6px），观感过度留白。
        float capInset = capSideInset(cornerRadius, contentHeight * 0.5f);
        float islandWidth = Math.max(0.0f, contentWidth + (capInset + PAD_X_EXTRA) * 2.0f);
        float islandX = ((float) mc.getWindow().getGuiScaledWidth() - islandWidth) / 2.0f;
        // 锚点 = 岛的几何中心：所有元素统一按「中心固定」定位（不再区分 TOP / CENTER），
        // 于是改大小或切换元素时岛都不会上下漂移。
        float offsetY = offsetYSetting.getValue().floatValue();
        float islandY = CENTER_ANCHOR_Y - islandHeight / 2.0f + offsetY;

        boolean editMode = isEditMode(mc);
        if (editMode) {
            // 中轴线画在岛之下
            drawCenterAxis(guiGraphics, mc);
        }

        PoseStack pose = guiGraphics.pose();

        boolean hasBackground = activeElement != null && activeElement.hasBackground();
        if (hasBackground) {
            // 胶囊底：圆角 = 高度/2（左右两个半圆）；RGB 随明暗主题（Dark = 黑、Light = 纯白），
            // alpha 由 Watermark 的 Background Opacity 滑条给出（10%~100% → 25~255，默认 16% ≈ zen 原 (0,0,0,40)）
            int alpha = Math.round(Mth.clamp(backgroundOpacityPercent, 0.0f, 100.0f) / 100.0f * 255.0f);
            int background = (alpha << 24) | ThemeHelper.surfaceRgb();
            ZenHudDraw.drawRoundedRect(pose, islandX, islandY, islandWidth, islandHeight,
                    cornerRadius, background);
        }
        // 内容在方形 scissor 内裁剪
        Renderer.pushScissor((int) islandX, (int) islandY, (int) islandWidth, (int) islandHeight);
        if (activeElement != null) {
            // 元素内部已按「目标字号」原生栅格化并原生布局，故 pose 缩放恒为 1（仅滑条拖动时临时补偿）；
            // 这里把元素摆在岛的对称内边距内（等价旧版以岛中心为锚点的居中）。
            float contentX = islandX + (islandWidth - contentWidth) / 2.0f;
            float contentY = islandY + (islandHeight - contentHeight) / 2.0f;
            activeElement.render(guiGraphics, contentX, contentY, contentWidth, contentHeight, progress);
        }
        Renderer.popScissor();

        // 记录本帧几何（命中测试 / 拖动钳制用），再画编辑框（屏幕空间、整数对齐 → 四边同粗）
        lastX = islandX;
        lastY = islandY;
        lastWidth = islandWidth;
        lastHeight = islandHeight;
        lastOffsetY = offsetY;
        if (editMode && islandWidth > 0.0f && islandHeight > 0.0f) {
            drawEditFrame(guiGraphics, islandX, islandY, islandWidth, islandHeight);
        }

        if (progress >= 1.0f) {
            outgoingElement = null;
        }
    }

    /**
     * 胶囊两端所需的水平内缩量：内容半高为 {@code h}、圆角半径（= 岛高一半）为 {@code r} 时，
     * 圆弧在内容区上下边缘处相对岛边缘内缩 {@code r − √(r² − h²)}。
     * 内容矩形的四角正好落在这条圆弧上，因此两端各让出该值即可，无需让出整个半圆。
     */
    private static float capSideInset(float r, float halfContent) {
        float inner = r * r - halfContent * halfContent;
        if (inner <= 0.0f) {
            return 0.0f;
        }
        return r - (float) Math.sqrt(inner);
    }

    // ---------------------------------------------------------------------
    // 布局编辑态（与 ModuleList 同款）
    // ---------------------------------------------------------------------

    /** ClickGUI 处于「E 按钮」折叠态即进入布局编辑态。 */
    private static boolean isEditMode(Minecraft mc) {
        return mc.screen instanceof NewClickGui gui && gui.isHidden();
    }

    /** 屏幕竖直中轴线：白色 30% 虚线（画整屏高）。 */
    private static void drawCenterAxis(GuiGraphics guiGraphics, Minecraft mc) {
        float axisX = (float) Math.round(mc.getWindow().getGuiScaledWidth() / 2.0f);
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        for (float y = 0.0f; y < screenHeight; y += AXIS_DASH + AXIS_GAP) {
            Renderer.drawRect(guiGraphics.pose(), axisX, y, 1.0f, AXIS_DASH, AXIS_COLOR);
        }
    }

    /** 布局编辑态边框：白色不透明度 80%（0xCCFFFFFF）的 1px 四边描边（照搬 ModuleList）。 */
    private static void drawEditFrame(GuiGraphics guiGraphics, float x, float y, float w, float h) {
        float fx = Math.round(x);
        float fy = Math.round(y);
        float fw = Math.round(w);
        float fh = Math.round(h);
        var pose = guiGraphics.pose();
        Renderer.drawRect(pose, fx, fy, fw, 1.0f, EDIT_FRAME_COLOR);                 // 上边
        Renderer.drawRect(pose, fx, fy + fh - 1.0f, fw, 1.0f, EDIT_FRAME_COLOR);     // 下边
        Renderer.drawRect(pose, fx, fy, 1.0f, fh, EDIT_FRAME_COLOR);                 // 左边
        Renderer.drawRect(pose, fx + fw - 1.0f, fy, 1.0f, fh, EDIT_FRAME_COLOR);     // 右边
    }

    // ---------------------------------------------------------------------
    // ClickGUI 折叠态的岛屿拖动（由 NewClickGui 路由）
    // ---------------------------------------------------------------------

    /** 当前上下拖动偏移。 */
    public float getOffsetY() {
        return offsetYSetting.getValue().floatValue();
    }

    /** 包围框（上一帧实际渲染位置）：{x0, y0, x1, y1}；无效时返回 null。 */
    public float[] getFrameExtents() {
        if (lastWidth <= 0.0f || lastHeight <= 0.0f) {
            return null;
        }
        return new float[]{lastX, lastY, lastX + lastWidth, lastY + lastHeight};
    }

    /** 屏幕点是否落在包围框内（±2px 容差，便于点中边缘）。 */
    public boolean isFrameHit(double mouseX, double mouseY) {
        float[] e = getFrameExtents();
        return e != null && mouseX >= e[0] - 2.0 && mouseX <= e[2] + 2.0
                && mouseY >= e[1] - 2.0 && mouseY <= e[3] + 2.0;
    }

    /**
     * 以 ny 为期望的 Y 偏移落点，钳制到「编辑框完整留在屏幕内」后写回。
     * 水平分量不参与（灵动岛恒定居中于中轴线）。
     */
    public void setDraggedOffsetY(float ny) {
        if (lastWidth <= 0.0f || lastHeight <= 0.0f) {
            return;
        }
        float screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        float baseY = lastY - lastOffsetY;                  // 零偏移时的 Y
        float min = -baseY;
        float max = screenHeight - baseY - lastHeight;
        if (max < min) {
            max = min;
        }
        offsetYSetting.setValue((double) Math.max(min, Math.min(max, ny)));
    }
}
