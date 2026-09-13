package xxliam.cookieclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import xxliam.cookieclient.modules.impl.movement.Scaffold;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;

import java.awt.Color;

/**
 * WatermarkHud：DynamicIsland 的常驻品牌水印（默认在无 Scaffold/告警时显示）。
 * <p>
 * 布局（单行、整组水平居中）：<b>版本号（左，次色） · Cookie（中，签名体 + 略大 + 白色） · 延迟（右，白色） · ●延迟指示点</b>。
 * <ul>
 *   <li>原先最左侧的 logo「C」列已删除；品牌与版本号原为上下堆叠（行距过密）现拆为单行；</li>
 *   <li>品牌 {@code Cookie} 用 <b>MomoSignature-Regular</b>（手写签名体，SIL OFL，随附 OFL 文本），字号比同排略大；</li>
 *   <li>延迟右侧<b>小圆点</b>：≤60ms 绿 / 61~120ms 黄 / ≥121ms 红。</li>
 * </ul>
 * <p>
 * <b>水平</b>：以品牌字中心对齐内容区中心，左右两翼取 {@code max(左翼, 右翼)}，内容区左右对称
 * （{@code 总宽 = 2 × 翼宽}），因此胶囊两端留白对称、Cookie 正对屏幕中线。
 * <p>
 * <b>垂直：所有文字按「墨迹包围盒的几何中心」对齐内容区（= 胶囊）几何中心</b>
 * —— 不是按字体度量盒、也不是按基线。墨迹位置由运行时<b>实测</b>得到
 * （{@link CustomFont#getGlyphRenderedBounds(char)} 扫描图谱里实际栅格化的像素、返回相对基线的偏移），
 * 而不是用「上探比例 × 字号」估算 —— 后者在字号连续变化时会与真实栅格化逐渐失配，
 * 表现就是「某些大小档位看着居中、另一些档位有细微偏移」。详见 {@link #baselineForInkCenter}。
 * <p>
 * 尺寸：zen 的原始视觉字号与布局常量一律乘 {@link IslandMetrics#scale()} 后<b>原生栅格化</b>
 * （不靠 pose 等比缩小，字形因此不糊）；<b>绘制与 {@link #size()} 共用同一套宽度计算</b>。
 * 背景默认带胶囊底；visible 与 Scaffold 互斥（Scaffold 开时让位）。
 */
public class WatermarkHud implements IHudElement {

    /** 灵动岛整体缩放（zen 原始单位 → 目标单位）；由 Size 滑条驱动，每帧 refreshScale() 刷新。 */
    private static float S;

    public static final String BRAND_LINE = CookieClientVersion.BRAND;
    public static final String VERSION_LINE = CookieClientVersion.VERSION;

    /** 列间距（zen 原值 12 × S）。 */
    private static float GAP;
    /** 内容区高度：原 25×S；品牌签名体墨迹高约 15px，抬高以留出上下余量。 */
    private static float CONTENT_HEIGHT;

    /** 品牌字视觉字号：同排其余文字 12×S，此处略大。 */
    private static float BRAND_VISUAL_SIZE;
    /** 同排小字（版本号 / 延迟）视觉字号。 */
    private static float SUB_VISUAL_SIZE;

    static {
        refreshScale();
    }

    /** 按当前大小档位重算全部与缩放相关的量（字号 / 间距 / 内容高）。 */
    private static void refreshScale() {
        S = IslandMetrics.scale();
        GAP = 12.0f * S;
        CONTENT_HEIGHT = 32.0f * S;
        BRAND_VISUAL_SIZE = 16f * S;
        SUB_VISUAL_SIZE = 12.0f * S;
    }

    /** {@code CustomFont.drawStringRGB} 内部的固定盒顶上移量（{@code translate(x, --y)}）。 */
    private static final float BOX_TOP_PIXEL_OFFSET = 1.0f;
    /** 统一垂直微调：0 = 严格按墨迹几何居中；正值整体下移（屏幕像素，不随大小档位缩放）。 */
    private static final float VERTICAL_NUDGE = 0.0f;

    /** 延迟指示圆点：直径、与 ms 的间距。 */
    private static final float DOT_SIZE = 4.5f;
    private static final float DOT_GAP = 4.0f;
    /** 延迟分档配色：≤60 绿 / ≤120 黄 / 其余红。 */
    private static final int PING_COLOR_GOOD = 0xFF3CCB5A;
    private static final int PING_COLOR_MID = 0xFFF5C542;
    private static final int PING_COLOR_BAD = 0xFFE5484D;

    private static final CustomFont font() {
        return FontStore.poppinsMedium(SUB_VISUAL_SIZE);
    }

    /** 品牌字（手写签名体）。 */
    private static final CustomFont brandFont() {
        return FontStore.momoSignature(BRAND_VISUAL_SIZE);
    }

    private static final int primaryColor = new Color(170, 170, 170).getRGB();
    private static final int shadowColor = new Color(0, 0, 0, 100).getRGB();

    private int lastTick = -1;
    private float pingWidth;
    private String pingText = "";
    private int pingValue = 1;

    private void updateCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || lastTick == mc.player.tickCount) {
            return;
        }
        lastTick = mc.player.tickCount;
        pingValue = getPingValue();
        pingText = pingValue + "ms";
        pingWidth = font().getStringWidth(pingText);
    }

    /**
     * 内容总宽 = 2 × 翼宽（与品牌中心对称）；翼宽 = 品牌半宽 + max(左翼, 右翼)，
     * 左翼 = 间距 + 版本号宽，右翼 = 间距 + 延迟宽 + 间距 + 圆点。
     * 这样品牌中心恰好落在内容区中心（Cookie 居中），左右留白对称。
     */
    private static float contentWidth(float brandWidth, float versionWidth, float pingWidth) {
        float leftWing = GAP + versionWidth;
        float rightWing = GAP + pingWidth + DOT_GAP + DOT_SIZE;
        return brandWidth + 2.0f * Math.max(leftWing, rightWing);
    }

    @Override
    public boolean hasBackground() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return Scaffold.INSTANCE == null || !Scaffold.INSTANCE.isEnabled();
    }

    @Override
    public void render(GuiGraphics guiGraphics, float x, float y, float width, float height, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || alpha <= 0.01f) {
            return;
        }
        refreshScale();
        updateCache();

        CustomFont subFont = font();
        CustomFont brand = brandFont();
        float versionWidth = subFont.getStringWidth(VERSION_LINE);
        float brandWidth = brand.getStringWidth(BRAND_LINE);

        // 内容区左右对称 ⇒ 内容区中心即胶囊中心；品牌中心对齐它 = Cookie 水平居中
        float brandCenterX = x + width / 2.0f;
        float brandLeft = brandCenterX - brandWidth / 2.0f;
        float centerY = y + height / 2.0f;
        int textColor = colorWithAlpha(Color.WHITE.getRGB(), alpha);
        int subColor = colorWithAlpha(primaryColor, alpha);
        int shadow = colorWithAlpha(shadowColor, alpha);

        // 每段各自按「那段文字的墨迹」居中：三段墨迹高度不同（版本号含 b/t 上探、延迟是数字），
        // 各自居中后它们的视觉重心落在同一条水平线上。
        float versionBaseline = baselineForInkCenter(centerY, subFont, VERSION_LINE);
        float brandBaseline = baselineForInkCenter(centerY, brand, BRAND_LINE);
        float pingBaseline = baselineForInkCenter(centerY, subFont, pingText);

        // 左：版本号（次色）
        drawText(guiGraphics, VERSION_LINE, brandLeft - GAP - versionWidth, versionBaseline, subFont, subColor, shadow);
        // 中：品牌 Cookie（签名体、略大、白色）
        drawText(guiGraphics, BRAND_LINE, brandLeft, brandBaseline, brand, textColor, shadow);
        // 右：延迟（白色）+ 分档圆点（圆心在中心线上）
        float pingLeft = brandLeft + brandWidth + GAP;
        drawText(guiGraphics, pingText, pingLeft, pingBaseline, subFont, textColor, shadow);
        float dotX = pingLeft + pingWidth + DOT_GAP;
        Renderer.drawRoundedRect(guiGraphics.pose(), dotX, centerY - DOT_SIZE / 2.0f, DOT_SIZE, DOT_SIZE,
                DOT_SIZE / 2.0f, colorWithAlpha(pingColor(pingValue), alpha));
    }

    /**
     * 让这段文字的<b>墨迹包围盒几何中心</b>落在 {@code centerY + VERTICAL_NUDGE} 所需的基线 Y。
     * <p>
     * <b>墨迹位置不靠「比例 × 字号」估算</b>：那样在字号连续变化（大小滑条）时会与真实栅格化结果
     * 逐渐失配（图谱里的墨迹是整数像素 + GASP hinting，比例并非严格恒定），表现就是
     * 「某些大小档位看着居中、另一些档位有细微偏移」。这里改为直接取
     * {@link CustomFont#getGlyphRenderedBounds(char)} —— 它扫描的是图谱里<b>实际栅格化的像素</b>，
     * 返回的 y 本身就是「相对基线」的偏移（GUI 单位），因此与最终上屏完全一致。
     * <p>
     * 仍需要的一处修正是 {@link #BOX_TOP_PIXEL_OFFSET}：{@code CustomFont.drawStringRGB} 内部
     * {@code translate(x, --y)} 会把盒顶再上移 1px，于是实际基线 = 传入的 baselineY − 1。
     */
    private static float baselineForInkCenter(float centerY, CustomFont font, String text) {
        return centerY + BOX_TOP_PIXEL_OFFSET - inkCenterRelativeToBaseline(font, text) + VERTICAL_NUDGE;
    }

    /**
     * 整串文字墨迹在垂直方向的中心相对<b>基线</b>的偏移（GUI 单位，向下为正）。
     * <p>
     * 逐字符取 {@link CustomFont#getGlyphRenderedBounds(char)}（其内部已按 char 缓存、且已扣掉
     * ascent ⇒ 相对基线），取垂直范围并集后求中心。空串 / 全空白时回落 0。
     */
    private static float inkCenterRelativeToBaseline(CustomFont font, String text) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\u00a7') {
                continue;
            }
            CustomFont.GlyphVisualBounds bounds = font.getGlyphRenderedBounds(c);
            if (bounds == null) {
                continue;
            }
            top = Math.min(top, bounds.y());
            bottom = Math.max(bottom, bounds.y() + bounds.height());
        }
        if (bottom < top) {
            return 0.0f;
        }
        return (top + bottom) / 2.0f;
    }

    /** 延迟分档配色：≤60ms 绿 / 61~120ms 黄 / ≥121ms 红。 */
    private static int pingColor(int pingMs) {
        if (pingMs <= 60) {
            return PING_COLOR_GOOD;
        }
        if (pingMs <= 120) {
            return PING_COLOR_MID;
        }
        return PING_COLOR_BAD;
    }

    /** 以给定基线绘制带阴影的两层文字（阴影为 +0.5 偏移的深色层）。 */
    private static void drawText(GuiGraphics guiGraphics, String text, float x, float baselineY, CustomFont font,
                                 int color, int shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ZenHudDraw.drawBaselineTwice(guiGraphics.pose(), font, text, x, baselineY, shadow, color);
    }

    @Override
    public Size size() {
        refreshScale();
        updateCache();
        CustomFont subFont = font();
        return new Size(contentWidth(brandFont().getStringWidth(BRAND_LINE),
                subFont.getStringWidth(VERSION_LINE), pingWidth), CONTENT_HEIGHT);
    }

    /** 延迟毫秒（单机固定 1ms）。分档圆点与文案共用该值。 */
    private int getPingValue() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSingleplayer()) {
            return 1;
        }
        int ping = 0;
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (playerInfo != null) {
                ping = playerInfo.getLatency();
            }
        }
        return Mth.clamp(ping, 0, 9999);
    }
}
