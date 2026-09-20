package xxliam.cookieclient.modules.impl.render.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.newclickgui.NewClickGui;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.MultiSelectSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * ModuleList：已启用模块列表（Arraylist）。
 * <p>
 * 复刻 opal {@code ToggledModulesElement} + {@code ModuleElement}：
 * 每游戏刻由 {@link #onTick()} 计算布局（文字/宽度/可见性/目标坐标），
 * 每帧由 {@link #render(GuiGraphics, float)} 推进动画并绘制。
 * 参数照搬 opal：OFFSET=12、行背景 {@code 0x80090909} 直角矩形、bar 宽 1 高 8 圆角 1、
 * 字号 8（productsans-medium）、主题渐变行色（interpolateColorsBackAndForth speed 6 / index*20）、
 * x 滑入 400ms EASE_OUT_EXPO、y 重排 600ms EASE_OUT_EXPO、禁用条目向右滑出(8)后移除。
 * <p>
 * 锚定侧：opal 原版 {@code ToggledModulesElement} 固定渲染在屏幕右缘，没有左缘模式。
 * 这里新增 {@code Side=Left}：把 opal 的右缘几何整体做水平镜像（行贴左缘、文字靠左、
 * bar 随 barMode 镜像到行内侧/外侧、隐藏目标 +8 对应滑出左缘），默认 Right 保持原版观感。
 * <p>
 * {@code Scale} 滑条(50%~150%，100=原大小)对整列做等比缩放，原点取 ModuleList 所在侧的顶角
 * （Right→屏幕右上角、Left→屏幕左上角）：行始终贴住顶角、向对角方向放大/收拢。
 */
public class ModuleList extends Module {

    public static ModuleList INSTANCE;
    /** 行高（opal ModuleElement.OFFSET = 12）。 */
    public static final float OFFSET = 12.0f;

    /** 行背景外圈晕开的模糊半径（opal 视觉参数，原 drawShadow 的第 5 参）。 */
    private static final float SHADOW_BLUR = 6.0f;

    // 每帧逐行几何缓存（复用数组，避免按行分配）——三段批次渲染需要先算完几何再分批发射
    private float[] rowBgLeft = new float[0];
    private float[] rowY = new float[0];
    private float[] rowW = new float[0];
    private float[] rowTextX = new float[0];
    private float[] rowBarX = new float[0];
    private int[] rowColor = new int[0];

    private void ensureRowBuffers(int count) {
        if (rowBgLeft.length >= count) {
            return;
        }
        rowBgLeft = new float[count];
        rowY = new float[count];
        rowW = new float[count];
        rowTextX = new float[count];
        rowBarX = new float[count];
        rowColor = new int[count];
    }

    /**
     * 布局拖动的轴模式开关（自由移动实现已保留，勿删，后期备用）：
     * true = 自由双向拖动（旧行为：可沿 x/y 任意挪动，边框不出屏）；
     * false = 贴边垂直模式（当前默认）：ModuleList 只能贴着所在屏幕边缘（Right→右缘 / Left→左缘）
     * 上下移动，水平偏移恒为 0；历史 X offset 仅持久化备用，渲染/命中一律忽略。
     */
    public static final boolean FREE_DRAG = false;

    /** 锚定侧：Right=opal 原版右缘；Left=镜像到左缘（opal 无此模式，属扩展）。 */
    private final ModeSetting side;
    /** 整列缩放百分比（50~150，100=原大小；除以 100 得等比缩放系数）。 */
    private final NumberSetting scale;
    /** X 拖动偏移（ClickGUI 隐藏时的拖拽态专用；隐藏设置项，仅用于持久化）。 */
    private final NumberSetting offsetX;
    /** Y 拖动偏移（同上）。 */
    private final NumberSetting offsetY;
    private final ModeSetting barMode;
    private final BooleanSetting lowercase;
    private final BooleanSetting showSuffix;
    /** 行背景不透明度（百分制 0-100，默认 50 = opal 原版 0x80090909 的 alpha 128/255）。 */
    private final NumberSetting backgroundOpacity;
    private final MultiSelectSetting visibleCategories;

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> visibleList = new ArrayList<>();
    private boolean synced;
    private boolean sortingDirty = true;
    private List<String> lastCategoryState = new ArrayList<>();
    private boolean lastLowercase = true;
    private boolean lastShowSuffix = true;

    public ModuleList() {
        super("ModuleList", Category.RENDER);
        INSTANCE = this;
        setVisible(false); // HUD 基础设施，不进 ModuleList 自身列表

        String[] categoryNames = new String[Category.values().length];
        for (int i = 0; i < Category.values().length; i++) {
            categoryNames[i] = Category.values()[i].getDisplayName();
        }
        side = new ModeSetting("Side", "Right", "Left").withDefault("Right");
        scale = new NumberSetting("Scale", 100, 50, 150, 1);
        offsetX = new NumberSetting("X offset", 0.0d, -10000.0d, 10000.0d, 1.0d, () -> false);
        offsetY = new NumberSetting("Y offset", 0.0d, -10000.0d, 10000.0d, 1.0d, () -> false);
        barMode = new ModeSetting("Bar mode", "Left", "Right", "None").withDefault("Left");
        lowercase = new BooleanSetting("Lowercase", true);
        showSuffix = new BooleanSetting("Show suffix", true);
        backgroundOpacity = new NumberSetting("Background opacity %", 50, 0, 100, 1);
        visibleCategories = new MultiSelectSetting("Visible categories", categoryNames).withDefaults(categoryNames);

        addSetting(scale);
        addSetting(offsetX);
        addSetting(offsetY);
        addSetting(side);
        addSetting(barMode);
        addSetting(lowercase);
        addSetting(showSuffix);
        addSetting(backgroundOpacity);
        addSetting(visibleCategories);
    }

    /** 注册前构造期无法访问 ModuleManager，首次 tick/render 时再同步全部模块。 */
    private void syncEntries() {
        if (synced) {
            return;
        }
        synced = true;
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            entries.add(new Entry(module));
        }
    }

    @Override
    public void onTick() {
        syncEntries();
        boolean low = lowercase.getValue();
        boolean suffix = showSuffix.getValue();

        for (Entry entry : entries) {
            entry.updateText(low, suffix);
        }

        // 布局失效检测：分类勾选 / Lowercase / Show suffix / 模块开关变化时重新按宽度降序排序
        // （对应 opal：PropertyUpdateEvent + 模块可见性变化 → markSortingDirty）
        List<String> categories = visibleCategories.getValue();
        if (!categories.equals(lastCategoryState)) {
            sortingDirty = true;
            lastCategoryState = new ArrayList<>(categories);
        }
        if (low != lastLowercase || suffix != lastShowSuffix) {
            sortingDirty = true;
            lastLowercase = low;
            lastShowSuffix = suffix;
        }
        for (Entry entry : entries) {
            boolean moduleVisible = isModuleVisible(entry);
            if (moduleVisible != entry.moduleWasVisible) {
                sortingDirty = true;
                entry.moduleWasVisible = moduleVisible;
            }
        }
        if (sortingDirty) {
            entries.sort((a, b) -> Float.compare(b.width, a.width));
            sortingDirty = false;
        }

        int index = 0;
        visibleList.clear();
        for (Entry entry : entries) {
            boolean moduleVisible = isModuleVisible(entry);
            entry.tick(index, moduleVisible);
            if (entry.isVisible()) {
                visibleList.add(entry);
            }
            if (moduleVisible) {
                index++;
            }
        }
    }

    private boolean isModuleVisible(Entry entry) {
        Module module = entry.module;
        return module.isVisible() && module.isEnabled()
                && visibleCategories.isSelected(module.getCategory().getDisplayName());
    }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }
        syncEntries();

        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        int[] colors = ThemeHelper.getThemeColors();
        String bar = barMode.getValue();
        boolean rightSide = "Right".equals(side.getValue());
        boolean leftBar = "Left".equals(bar);

        // ClickGUI 处于隐藏（折叠）态时进入「布局编辑态」：整列被白色半透明框圈住，可拖动
        boolean editMode = mc.screen instanceof NewClickGui gui && gui.isHidden();

        // 整列偏移（拖动）→ 等比缩放（原点 = ModuleList 所在侧顶角）。偏移在缩放之外：
        // 先 translate 把整块从默认贴边位置挪开，再以顶角为轴缩放，两者互不影响。
        float ox = getEffectiveOffsetX();
        float oy = getOffsetY();
        float factor = scale.getValue().floatValue() / 100.0f;
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(ox, oy, 0.0f);
        RenderHelper.pushScaleAround(pose, rightSide ? (float) scaledWidth : 0.0f, 0.0f, factor);

        // ---------------------------------------------------------------------
        // 三段批次渲染（把每行的 6 次 draw 合并成 3 次总 draw）
        // ---------------------------------------------------------------------
        // 原实现逐行调 drawShadow / drawRect / drawRoundedRect ×2 / drawStringWithShadow：
        // 每行 6 次独立 draw（各自 setShader + begin/end + 上传），20 行 ≈ 120 次；
        // 其中 drawShadow 还按「宽度×高度」缓存模糊纹理 —— 每行宽度都不同，
        // 于是每行一张独立纹理、每行一次纹理换绑，实测把 200fps 压到 120fps。
        // 现在：①阴影批次共用一张与宽度无关的九宫格模糊纹理；②行底与两条 1px 竖条合并进
        // 矩形批次；③所有行的文字（阴影+主色两遍）合并进 CustomFont 的批量文字通道。
        int rowCount = visibleList.size();
        ensureRowBuffers(rowCount);

        // 行背景不透明度（百分制 0~100，默认 50 = opal 原版 0x80090909 的 alpha 128/255）。
        // 参数名带 % 后缀：老配置里的 "Background opacity"（0~255）不再被读取，
        // 否则 137 这种旧值会被当 137% 换算后溢出到红通道。
        int bgAlpha = Math.round(Math.max(0.0f, Math.min(100.0f, backgroundOpacity.getValue().floatValue()))
                / 100.0f * 255.0f);
        // 底色随明暗主题：Dark = opal 原色 0x090909；Light = 纯白（alpha 语义不变，仍由滑条控制）。
        // 边缘晕开的 tint 是「阴影」——按需求不随明暗反转，恒用原来的近黑色。
        int bgColor = (bgAlpha << 24) | ThemeHelper.surfaceRgb();
        int shadowTint = (bgAlpha << 24) | 0x00090909;
        boolean hasBar = !"None".equals(bar);
        float textOffset = leftBar ? 2.0f : "None".equals(bar) ? 3.5f : 4.25f;

        // ---- 第 1 遍：推进动画 + 算出本帧几何（缓存到复用数组，避免逐行分配）----
        for (int i = 0; i < rowCount; i++) {
            Entry entry = visibleList.get(i);
            entry.xAnim.animate(entry.posX, 0.4, Easings.EASE_OUT_EXPO);
            entry.yAnim.animate(entry.posY, 0.6, Easings.EASE_OUT_EXPO);
            entry.xAnim.tick();
            entry.yAnim.tick();

            // 虚拟坐标沿用 opal：value = posX 目标（静止=-width / 滑出=8），右缘渲染 x = value + scaledWidth
            float vx = entry.xAnim.getValueF();
            float w = entry.width;
            float bgLeft;
            float textX;
            float barX;
            if (rightSide) {
                // opal 原版右缘几何
                float posXr = vx + scaledWidth;
                bgLeft = posXr - 6.5f;
                textX = posXr - textOffset;
                barX = leftBar ? posXr - 4.5f : posXr + w - 2.5f;
            } else {
                // 左缘 = 右缘几何关于 scaledWidth 中轴的水平镜像（行贴左缘、文字靠左）
                bgLeft = -vx - w;                                        // 行背景左缘
                textX = textOffset - vx - w;                             // 文字左缘
                barX = leftBar ? 3.5f - vx : 1.5f - vx - w;              // bar 镜像到行内侧/外侧
            }
            rowBgLeft[i] = bgLeft;
            rowY[i] = entry.yAnim.getValueF();
            rowW[i] = w;
            rowTextX[i] = textX;
            rowBarX[i] = barX;
            rowColor[i] = ColorUtil.interpolateColorsBackAndForth(6, i * 20, colors[0], colors[1]);
        }

        // ---- 第 2 遍：阴影（一次 draw）----
        if (rowCount > 0) {
            Renderer.beginShadowBatch(SHADOW_BLUR, OFFSET);
            for (int i = 0; i < rowCount; i++) {
                // 行背景直角矩形之外再晕开一圈（tint 与行底同色同 alpha，blur = SHADOW_BLUR）
                Renderer.batchShadow(pose, rowBgLeft[i], rowY[i], rowW[i] + 6.5f, OFFSET, shadowTint);
            }
            Renderer.endShadowBatch();
        }

        // ---- 第 3 遍：行底 + 两条 1px 竖条（一次 draw）----
        if (rowCount > 0) {
            Renderer.beginRectBatch();
            for (int i = 0; i < rowCount; i++) {
                // 行背景（直角矩形，opal rect(posX-6.5F, posY, width+6.5F, OFFSET, 0x80090909)）；
                // alpha 由 Background opacity % 滑块(0-100)控制，默认 50 = opal 原版 0x80
                Renderer.batchRect(pose, rowBgLeft[i], rowY[i], rowW[i] + 6.5f, OFFSET, bgColor);
                if (hasBar) {
                    // 竖条宽 1px：圆角半径 1 在 1px 宽上不可见，故并入矩形批次（省掉两行两次圆角 shader draw）。
                    // 阴影条按右缘 +0.5px 右下 / 左缘 -0.5px 左下偏移。
                    float shadowX = rightSide ? rowBarX[i] + 0.5f : rowBarX[i] - 0.5f;
                    Renderer.batchRect(pose, shadowX, rowY[i] + 2.5f, 1.0f, 8.0f, ColorUtil.getShadowColor(rowColor[i]));
                    Renderer.batchRect(pose, rowBarX[i], rowY[i] + 2.0f, 1.0f, 8.0f, rowColor[i]);
                }
            }
            Renderer.endRectBatch();
        }

        // ---- 第 4 遍：文字（每行「阴影 + 主色」两遍，整批一次 draw）----
        // 注：cookie CustomFont 是「顶锚定」(penY 即 glyph 顶)，与 NVG baseline 锚定不同——
        // 直接照搬 opal 的 posY+9 会让 glyph 顶跑到 posY+8、行外越界。
        if (rowCount > 0) {
            CustomFont font = FontStore.PRODUCTSANS_MEDIUM_8;
            // 先把所有行的字形预热（getOrLoadGlyph 带缓存，这里只是把还没加载的补齐）：
            // 这样 appendText 期间不会再栅格化新字形 → 不会中途 flush/换页，整批就是一次 draw。
            ResourceLocation textPage = null;
            for (int i = 0; i < rowCount; i++) {
                ResourceLocation page = font.glyphPageFor(visibleList.get(i).text);
                if (textPage == null) {
                    textPage = page;
                }
            }
            if (textPage != null) {
                Matrix4f matrix = new Matrix4f(pose.last().pose());
                font.beginTextBatch(textPage);
                int shadowArgb = CustomFont.shadowArgb();
                for (int i = 0; i < rowCount; i++) {
                    Entry entry = visibleList.get(i);
                    font.appendText(matrix, entry.text, rowTextX[i] + 0.5f, rowY[i] + 3.0f, shadowArgb);
                    font.appendText(matrix, entry.text, rowTextX[i], rowY[i] + 2.5f, rowColor[i]);
                }
                font.endTextBatch();
            }
        }

        RenderHelper.popPose(pose);
        pose.popPose();

        // ClickGUI 隐藏态的编辑框：白色 80% 边框，宽=顶部长度（最宽行的全长）、高=贴屏边缘那列的高度。
        // 画在屏幕空间（getFrameExtents 已含 Scale + 拖动偏移，与 isFrameHit 命中同源），坐标取整：
        // 变换空间内的小数坐标会让四条边落在不同亚像素相位，光栅化后横竖边粗细不一；
        // 整数对齐后四条边完全同粗（1 GUI 单位 × guiScale 物理像素）。
        if (editMode && visibleList.size() > 0) {
            float[] e = getFrameExtents();
            if (e != null) {
                float fx0 = (float) Math.round(e[0]);
                float fy0 = (float) Math.round(e[1]);
                float fx1 = (float) Math.round(e[2]);
                float fy1 = (float) Math.round(e[3]);
                drawEditFrame(guiGraphics.pose(), fx0, fy0, fx1 - fx0, fy1 - fy0);
            }
        }
    }

    /** 布局编辑态边框：Dark 白 80% / Light 近黑 80%（1px 四边描边，必须与明暗底色有对比）。 */
    private static void drawEditFrame(PoseStack pose, float x, float y, float w, float h) {
        int border = ThemeHelper.isLight() ? 0xCC101010 : 0xCCFFFFFF;
        Renderer.drawRect(pose, x, y, w, 1.0f, border);                  // 上边
        Renderer.drawRect(pose, x, y + h - 1.0f, w, 1.0f, border);       // 下边
        Renderer.drawRect(pose, x, y, 1.0f, h, border);                  // 左边
        Renderer.drawRect(pose, x + w - 1.0f, y, 1.0f, h, border);       // 右边
    }

    // ---------------------------------------------------------------------
    // ClickGUI 隐藏态拖动（由 NewClickGui 在 hidden 时路由）
    // ---------------------------------------------------------------------

    public float getOffsetX() {
        return offsetX.getValue().floatValue();
    }

    public float getOffsetY() {
        return offsetY.getValue().floatValue();
    }

    /**
     * 水平拖动偏移在渲染 / 编辑框命中中的实际取值：
     * 贴边垂直模式（{@link #FREE_DRAG}=false）恒为 0（列表贴着屏幕边缘），
     * X offset 设置项原值保留不动，仅作后期开启自由移动时的备用。
     */
    public float getEffectiveOffsetX() {
        return FREE_DRAG ? getOffsetX() : 0.0f;
    }

    /** 以 (nx, ny) 为期望偏移落点，钳制到屏幕内后写回（边框不出屏）。 */
    public void setDraggedOffset(float nx, float ny) {
        float[] limits = getOffsetLimits();
        if (limits == null) {
            return;
        }
        offsetX.setValue((double) Math.max(limits[0], Math.min(limits[1], nx)));
        offsetY.setValue((double) Math.max(limits[2], Math.min(limits[3], ny)));
    }

    /** 包围框（含 Scale + 偏移换算后的屏幕坐标）：{x0, y0, x1, y1}；无可视行返回 null。 */
    public float[] getFrameExtents() {
        float[] noOffset = computeNoOffsetExtents();
        if (noOffset == null) {
            return null;
        }
        float ox = getEffectiveOffsetX();
        float oy = getOffsetY();
        return new float[]{noOffset[0] + ox, noOffset[1] + oy, noOffset[2] + ox, noOffset[3] + oy};
    }

    /** 屏幕点是否落在包围框内（±2px 容差，便于点中边缘）。 */
    public boolean isFrameHit(double mouseX, double mouseY) {
        float[] e = getFrameExtents();
        return e != null && mouseX >= e[0] - 2.0 && mouseX <= e[2] + 2.0
                && mouseY >= e[1] - 2.0 && mouseY <= e[3] + 2.0;
    }

    /** 偏移允许范围 {minX, maxX, minY, maxY}：保证框完全落在屏幕内。 */
    public float[] getOffsetLimits() {
        float[] e = computeNoOffsetExtents();
        if (e == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        return new float[]{-e[0], sw - e[2], -e[1], sh - e[3]};
    }

    /**
     * 包围框在零偏移下的屏幕极值 {x0, y0, x1, y1}：
     * 行背景基坐标经「顶角为原点」的等比例缩放后，再与拖动偏移线性相加。
     */
    private float[] computeNoOffsetExtents() {
        if (visibleList.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        boolean rightSide = "Right".equals(side.getValue());
        float factor = scale.getValue().floatValue() / 100.0f;

        float minXb = Float.MAX_VALUE, maxXb = Float.MIN_VALUE;
        float minYb = Float.MAX_VALUE, maxYb = Float.MIN_VALUE;
        for (Entry entry : visibleList) {
            float vx = entry.xAnim.getValueF();
            float posY = entry.yAnim.getValueF();
            float w = entry.width;
            float bgLeft = rightSide ? vx + scaledWidth - 6.5f : -vx - w;
            minXb = Math.min(minXb, bgLeft);
            maxXb = Math.max(maxXb, bgLeft + w + 6.5f);
            minYb = Math.min(minYb, posY);
            maxYb = Math.max(maxYb, posY + OFFSET);
        }
        float cornerX = rightSide ? (float) scaledWidth : 0.0f;
        return new float[]{
                cornerX + factor * (minXb - cornerX),
                factor * minYb,
                cornerX + factor * (maxXb - cornerX),
                factor * maxYb
        };
    }

    /** 每个模块的列表条目：文字/宽度/目标坐标 + x/y 动画（照搬 opal ModuleElement）。 */
    private static final class Entry {
        final Module module;
        final SmoothAnimationTimer xAnim = new SmoothAnimationTimer();
        final SmoothAnimationTimer yAnim = new SmoothAnimationTimer();

        boolean moduleWasVisible;
        boolean visible;      // 是否处于显示列表（含滑出中）
        boolean disabled;     // 滑出中（模块已关但动画未结束）
        String text = "";
        float width;
        float posX = 8.0f;    // 目标 x 偏移：显示=-width，滑出/隐藏=8
        float posY;

        Entry(Module module) {
            this.module = module;
        }

        void updateText(boolean lowercase, boolean showSuffix) {
            String name = module.getName();
            String suffix = module.getSuffix();
            String text = (suffix == null || !showSuffix) ? name : name + " \u00a77" + suffix;
            if (lowercase) {
                text = text.toLowerCase();
            }
            this.text = text;
            this.width = FontStore.PRODUCTSANS_MEDIUM_8.getStringWidth(text);
        }

        /** 布局更新（opal ModuleElement.tick：updateVisibility + updatePosition）。 */
        void tick(int index, boolean moduleVisible) {
            if (!moduleVisible) {
                if (visible) {
                    // 滑出动画结束后才移除（opal：xAnimation.isFinished && disabled）
                    if (disabled && !xAnim.isAnimating()) {
                        visible = false;
                        return;
                    }
                    disabled = true;
                }
            } else {
                disabled = false;
            }

            posX = disabled ? 8.0f : -width;

            if (moduleVisible) {
                boolean fresh = !visible;
                visible = true;
                posY = index * OFFSET;
                if (fresh) {
                    // 从屏外右缘滑入：x 从 8 开始，y 直接落到目标行（对应 opal 新建动画初值）
                    xAnim.setCurrentValue(8.0f);
                    yAnim.setCurrentValue(posY);
                }
            }
        }

        boolean isVisible() {
            return visible;
        }
    }
}
