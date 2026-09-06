package xxliam.cookieclient.gui.dropdownclickgui;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.render.CustomFont;

/**
 * Dropdown GUI 文本定位换算工具。
 * <p>
 * Opal 走 NanoVG：文本 {@code y} 是 baseline 或 对齐中线；cookie {@code CustomFont#drawString}
 * 是「格顶锚定」（penY 即 glyph 顶）。为照搬 opal 视觉参数，这里集中做锚点换算：
 * <ul>
 *     <li>{@link #baseline(GuiGraphics, CustomFont, String, float, float, int)}：给定 NVG
 *         baseline y，换算顶锚（ascent 取 AWT 度量 / 内部 scale，与 OpalEspRenderer /
 *         Notifications 既有换算一致）。</li>
 *     <li>{@link #iconCentered(...)}：图标以 (cx,cy) 为字形外框中心（对应 opal 的
 *         {@code NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE}）。</li>
 * </ul>
 */
public final class DropdownRender {

    private DropdownRender() {
    }

    /** NVG baseline → cookie 顶锚绘制。 */
    public static void baseline(GuiGraphics g, CustomFont font, String text, float x, float baselineY, int color) {
        float top = baselineY - font.getFontMetrics().getAscent() / (float) font.getScale();
        font.drawString(g.pose(), text, x, top, color);
    }

    /** 以 (cx, cy) 为中心绘制单行文本（水平/垂直均居中，vertical 用字高一半近似 NVG MIDDLE）。 */
    public static void centered(GuiGraphics g, CustomFont font, String text, float cx, float cy, int color) {
        float top = cy - font.getFontHeight() / 2.0f;
        font.drawString(g.pose(), text, cx - font.getStringWidth(text) / 2.0f, top, color);
    }

    /** 以 (cx, cy) 为中心绘制图标字形（图标近似方形，直接用字符串宽与字高）。 */
    public static void iconCentered(GuiGraphics g, CustomFont font, String icon, float cx, float cy, int color) {
        centered(g, font, icon, cx, cy, color);
    }
}
