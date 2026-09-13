package xxliam.cookieclient.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * DynamicIsland 内轮换的 HUD 元素接口。
 * <p>
 * 照搬 OpenZen {@code shit.zen.hud.IHudElement}（字段命名规整：原版的
 * {@code getHudAlignment()}→{@link #size()}、{@code getHudSize()}→{@link #alignment()}，
 * 语义一一对应）。每个元素同时承担：
 * <ul>
 *   <li>{@link #isVisible()} 决定是否被 {@code DynamicIsland} 选中显示（按列表顺序取首个可见）；</li>
 *   <li>{@link #size()} 提供当前内容的期望宽高（弹簧动画目标值）；</li>
 *   <li>{@link #alignment()} 决定岛在顶栏区域内的垂直锚定（TOP / CENTER）；</li>
 *   <li>{@link #render(GuiGraphics, float, float, float, float, float)} 在岛内部绘制内容
 *       （已在 1.20.1 {@code GuiGraphics} 上下文内，坐标全为 GUI 逻辑像素）。</li>
 * </ul>
 */
public interface IHudElement {

    enum Alignment {
        TOP, BOTTOM, LEFT, RIGHT, CENTER
    }

    record Size(float width, float height) {
    }

    /** 锚定方向（原版 getHudSize）。 */
    default Alignment alignment() {
        return Alignment.TOP;
    }

    /** 是否带深色圆角背景（所有 zen 元素都返回 true）。 */
    default boolean hasBackground() {
        return false;
    }

    /** 是否当前可见（供 {@code ActiveElementSelector} 轮换选择）。 */
    default boolean isVisible() {
        return true;
    }

    /** 期望内容尺寸（原版 getHudAlignment）。 */
    default Size size() {
        return new Size(240.0f, 25.0f);
    }

    /** 把 ARGB 颜色按 alpha（0~1）整体缩放透明度。照搬 zen。 */
    default int colorWithAlpha(int color, float alpha) {
        float clamped = Math.max(0.0f, Math.min(1.0f, alpha));
        return color & 0xFFFFFF | (int) (255.0f * clamped) << 24;
    }

    /** 在岛内绘制内容（x/y/w/h 为岛在屏幕上的逻辑坐标）。 */
    void render(GuiGraphics guiGraphics, float x, float y, float w, float h, float alpha);
}
