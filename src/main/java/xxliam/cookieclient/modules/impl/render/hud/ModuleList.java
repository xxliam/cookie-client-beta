package xxliam.cookieclient.modules.impl.render.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.MultiSelectSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;
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
 */
public class ModuleList extends Module {

    public static ModuleList INSTANCE;
    /** 行高（opal ModuleElement.OFFSET = 12）。 */
    public static final float OFFSET = 12.0f;

    /** 锚定侧：Right=opal 原版右缘；Left=镜像到左缘（opal 无此模式，属扩展）。 */
    private final ModeSetting side;
    private final ModeSetting barMode;
    private final BooleanSetting lowercase;
    private final BooleanSetting showSuffix;
    /** 行背景不透明度（0-255，默认 128 = opal 原版 0x80090909 的 alpha）。 */
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
        barMode = new ModeSetting("Bar mode", "Left", "Right", "None").withDefault("Left");
        lowercase = new BooleanSetting("Lowercase", true);
        showSuffix = new BooleanSetting("Show suffix", true);
        backgroundOpacity = new NumberSetting("Background opacity", 128, 0, 255, 1);
        visibleCategories = new MultiSelectSetting("Visible categories", categoryNames).withDefaults(categoryNames);

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

        for (int i = 0; i < visibleList.size(); i++) {
            Entry entry = visibleList.get(i);

            entry.xAnim.animate(entry.posX, 0.4, Easings.EASE_OUT_EXPO);
            entry.yAnim.animate(entry.posY, 0.6, Easings.EASE_OUT_EXPO);
            entry.xAnim.tick();
            entry.yAnim.tick();

            // 虚拟坐标沿用 opal：value = posX 目标（静止=-width / 滑出=8），右缘渲染 x = value + scaledWidth
            float vx = entry.xAnim.getValueF();
            float posY = entry.yAnim.getValueF();
            int rowColor = ColorUtil.interpolateColorsBackAndForth(6, i * 20, colors[0], colors[1]);

            float textOffset = leftBar ? 2.0f : "None".equals(bar) ? 3.5f : 4.25f;
            float w = entry.width;
            float bgLeft, textX, barX;
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

            // 行背景（直角矩形，opal rect(posX-6.5F, posY, width+6.5F, OFFSET, 0x80090909)）；
            // alpha 由 Background opacity 滑块(0-255)控制，默认 128 = opal 原版 0x80
            // 边缘晕开：drawShadow tint 用与背景同色同 alpha，高斯纹理 alpha 中心→边缘天然衰减，
            // 视觉上仅外圈柔和淡出（中心高 alpha 区被 background rect 覆盖主导，不会在面板上
            // 形成「行进色图层覆盖」感）；blur=6。
            // 注：cookie CustomFont drawStringRGB 是「顶锚定」(penY 即 glyph 顶)，与 NVG baseline
            // 锚定不同——直接照搬 opal 的 posY+9 在这里会让 glyph 顶跑到 posY+8、行外越界。
            int bgAlpha = Math.max(0, Math.min(255, backgroundOpacity.getValue().intValue()));
            int bgColor = (bgAlpha << 24) | 0x00090909;
            Renderer.drawShadow(guiGraphics.pose(), bgLeft, posY, w + 6.5f, OFFSET, 6.0f, bgColor);
            Renderer.drawRect(guiGraphics.pose(), bgLeft, posY, w + 6.5f, OFFSET, bgColor);

            if (!"None".equals(bar)) {
                // 阴影（右缘 +0.5px 右下；左缘镜像为 -0.5px 左下）
                float shadowX = rightSide ? barX + 0.5f : barX - 0.5f;
                Renderer.drawRoundedRect(guiGraphics.pose(), shadowX, posY + 2.5f, 1.0f, 8.0f, 1.0f,
                        ColorUtil.getShadowColor(rowColor));
                Renderer.drawRoundedRect(guiGraphics.pose(), barX, posY + 2.0f, 1.0f, 8.0f, 1.0f, rowColor);
            }

            FontStore.PRODUCTSANS_MEDIUM_8.drawStringWithShadow(guiGraphics.pose(), entry.text,
                    textX, posY + 2.5f, rowColor);
        }
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
