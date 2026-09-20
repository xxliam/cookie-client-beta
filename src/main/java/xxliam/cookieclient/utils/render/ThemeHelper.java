package xxliam.cookieclient.utils.render;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.impl.render.Theme;

/**
 * 主题辅助：从 Theme 模块读取当前配色 (主色, 副色) 与 GUI 明暗调色板。
 * <p>
 * Theme 模块未注册 / 未构造时全部回退 opal 默认主题 + Dark 明暗。
 * <p>
 * 明暗调色板（{@link #surface}/{@link #foreground}/{@link #control}/{@link #overlay}）是
 * 新增 Theme: Dark|Light 之后所有自绘 UI 取色的统一入口——**阴影（shadow）不在其中**，
 * 阴影一律保持原黑色，不随明暗反转。
 */
public final class ThemeHelper {

    private ThemeHelper() {
    }

    /** 取 Theme 模块实例（未注册 / 未构造时 null）。 */
    private static Theme module() {
        Theme theme = Theme.INSTANCE;
        if (theme == null && CookieClient.MODULE_MANAGER != null) {
            Object found = CookieClient.MODULE_MANAGER.getModule("Theme");
            if (found instanceof Theme t) {
                theme = t;
            }
        }
        return theme;
    }

    /** 返回 [主色, 副色] ARGB。 */
    public static int[] getThemeColors() {
        Theme theme = module();
        if (theme != null) {
            return theme.getColors();
        }
        return new int[]{0xFF2DBFFE, 0xFF2499CB}; // opal 默认 Opal 主题
    }

    // ---------------------------------------------------------------------
    // GUI 明暗调色板
    // ---------------------------------------------------------------------

    /**
     * 当前是否 Light 明暗主题（Theme 模块的 {@code Theme} 参数 = Light）。
     * <p>
     * Theme 模块不可用时按 Dark —— 即「拿不到主题」时保持原有观感。
     */
    public static boolean isLight() {
        Theme theme = module();
        return theme != null && theme.isLight();
    }

    /** 背景底色 RGB（不含 alpha）：Dark = 面板原色 23,23,23；Light = 纯白。 */
    public static int surfaceRgb() {
        return isLight() ? 0xFFFFFF : 0x171717;
    }

    /** 背景底色（面板 / 卡片 / 灵动岛 / ModuleList 行），给定 alpha 缩放。 */
    public static int surface(float alphaScale) {
        return ColorUtil.withAlpha(0xFF000000 | surfaceRgb(), alphaScale);
    }

    /** 前景色（主文字、开关圆钮一类「压在底色上的实心元素」）：Dark = 白；Light = 近黑。 */
    public static int foreground(float alphaScale) {
        return ColorUtil.withAlpha(isLight() ? 0xFF1A1A1A : 0xFFFFFFFF, alphaScale);
    }

    /**
     * 次要前景（设置项名称、HEX、说明文字）：同 {@link #foreground} 但默认压到 80% 不透明度
     * —— 对应各处原本的 {@code withAlpha(-1, alpha * 0.8f)}。
     */
    public static int secondary(float alphaScale) {
        return foreground(alphaScale * 0.8f);
    }

    /**
     * 灰阶自适应：Dark 原样返回调用处的灰阶 {@code darkRgb}，Light 返回其朴素反相（{@code 255 − c}）。
     * <p>
     * 用于 HUD 元素里的轨道底色 / 次要文字 / 图标底片等——这些值在 Dark 下必须**逐字保持不变**，
     * 在 Light 下取「等对比度」的反相值（30 → 225、170 → 85、128 → 127）。背景不适用本函数
     * （背景要纯白，不能是 255−23=232），那种场合用 {@link #surface(float)}。
     */
    public static int shade(int darkRgb, float alphaScale) {
        int rgb = darkRgb & 0xFFFFFF;
        if (isLight()) {
            rgb = 0xFFFFFF - rgb;
        }
        return ColorUtil.withAlpha(0xFF000000 | rgb, alphaScale);
    }

    /** 控件底（下拉面板 / 滑条轨道 / 开关轨道）：Dark = 60,60,60；Light = 210,210,210。 */
    public static int control(float alphaScale) {
        return ColorUtil.withAlpha(controlRgb() | 0xFF000000, alphaScale);
    }

    /** 控件底 RGB（不含 alpha，三通道同值）：Dark = 60,60,60；Light = 210,210,210。 */
    public static int controlRgb() {
        int channel = isLight() ? 0xD2 : 0x3C;
        return (channel << 16) | (channel << 8) | channel;
    }

    /**
     * hover 态控件底 RGB：偏移量与 zen 原实现一致（+30 灰阶）——Dark 下提亮、Light 下压暗
     * （浅灰再提亮会糊成一片，故按明暗取符号）。
     */
    public static int controlHoverRgb(float hoverAmount) {
        int shift = Math.round(30.0f * Math.min(1.0f, Math.max(0.0f, hoverAmount))) * (isLight() ? -1 : 1);
        int channel = Math.max(0, Math.min(255, ((controlRgb() >> 16) & 0xFF) + shift));
        return (channel << 16) | (channel << 8) | channel;
    }

    /**
     * hover / 选中高亮叠加层：Dark = 白（提亮）；Light = 黑（压暗）。
     * 调用处传原本给白色的 alpha 倍率即可。
     */
    public static int overlay(float alphaScale) {
        return ColorUtil.withAlpha(isLight() ? 0xFF000000 : 0xFFFFFFFF, alphaScale);
    }

    /** 由底色向外淡出的渐变色基准（顶部渐隐等）：Dark = 黑；Light = 白。 */
    public static int fadeRgb() {
        return isLight() ? 0xFFFFFF : 0x000000;
    }
}
