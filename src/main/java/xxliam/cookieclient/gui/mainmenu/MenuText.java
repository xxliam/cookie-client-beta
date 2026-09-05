package xxliam.cookieclient.gui.mainmenu;

import com.mojang.blaze3d.vertex.PoseStack;
import xxliam.cookieclient.render.CustomFont;

/**
 * Setsuna 文本语义适配层：复刻 SkijaUi text/boldText 的定位规则。
 * <p>
 * Skia 以「文字框 top + 框高 height」把文本垂直居中（baseline = top + (height - textHeight)/2 - ascent，
 * ascent<0）；cookie CustomFont 以「行顶部 y」为入口。此处用同款 AWT 字体度量换算：
 * 先把 Setsuna 框高换算成 baseline，再映射回 cookie 的 y = baseline - ascent + 1。
 */
public final class MenuText {

    private MenuText() {
    }

    public static float textWidth(String text, float size) {
        return safeText(text).isEmpty() ? 0.0F : MenuFonts.regular(size).getStringWidth(safeText(text));
    }

    public static float boldTextWidth(String text, float size) {
        return safeText(text).isEmpty() ? 0.0F : MenuFonts.bold(size).getStringWidth(safeText(text));
    }

    /** Setsuna SkiaUi.text(canvas, text, x, top, height, color, size)。 */
    public static void text(PoseStack poseStack, String text, float x, float top, float height,
                            int color, float size) {
        draw(poseStack, MenuFonts.regular(size), text, x, top, height, color, size);
    }

    /** 默认字号 9 的文本（Setsuna SkiaUi.text 无 size 重载）。 */
    public static void text(PoseStack poseStack, String text, float x, float top, float height,
                            int color) {
        text(poseStack, text, x, top, height, color, 9.0F);
    }

    /** Setsuna SkiaUi.boldText(canvas, text, x, top, height, color, size)。 */
    public static void boldText(PoseStack poseStack, String text, float x, float top, float height,
                                int color, float size) {
        draw(poseStack, MenuFonts.bold(size), text, x, top, height, color, size);
    }

    /** 默认字号 9 的加粗文本。 */
    public static void boldText(PoseStack poseStack, String text, float x, float top, float height,
                                int color) {
        boldText(poseStack, text, x, top, height, color, 9.0F);
    }

    /** 带固定字距的加粗文本（UiControls.brand 语义）。 */
    public static void boldTextTracked(PoseStack poseStack, String text, float x, float top,
                                       float height, int color, float size, float tracking) {
        String value = safeText(text);
        if (value.isEmpty()) {
            return;
        }
        CustomFont font = MenuFonts.bold(size);
        font.setLetterSpacing(tracking);
        try {
            draw(poseStack, font, value, x, top, height, color, size);
        } finally {
            font.resetLetterSpacing();
        }
    }

    /** 带固定字距的加粗文本宽度。 */
    public static float boldTextWidthTracked(String text, float size, float tracking) {
        String value = safeText(text);
        if (value.isEmpty()) {
            return 0.0F;
        }
        return boldTextWidth(value, size) + tracking * (value.length() - 1);
    }

    /** 系统符号字形（pf 未覆盖字符的兜底，如 ⇄）。 */
    public static float symbolWidth(String text, float size) {
        String value = safeText(text);
        return value.isEmpty() ? 0.0F : MenuFonts.symbol(size).getStringWidth(value);
    }

    public static void symbolText(PoseStack poseStack, String text, float x, float top,
                                  float height, int color, float size) {
        draw(poseStack, MenuFonts.symbol(size), text, x, top, height, color, size);
    }

    public static boolean canDisplaySymbol(int codePoint) {
        return MenuFonts.canDisplaySymbol(codePoint);
    }

    private static void draw(PoseStack poseStack, CustomFont font, String text,
                             float x, float top, float boxHeight, int color, float size) {
        String value = safeText(text);
        if (value.isEmpty()) {
            return;
        }
        int scale = font.getScale();
        float ascent = font.getFontMetrics().getAscent() / (float) scale;
        float descent = font.getFontMetrics().getDescent() / (float) scale;
        float textHeight = ascent + descent;
        // Setsuna 的 Skia baseline（AWT 坐标系下 ascent 为正）
        float baseline = top + (boxHeight - textHeight) * 0.5F + ascent;
        // cookie CustomFont 的 y 顶 = ascent 线 - 1px（其内部 translate(x, y-1)）
        float y = baseline - ascent + 1.0F;
        font.drawString(poseStack, value, x, y, color);
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }
}
