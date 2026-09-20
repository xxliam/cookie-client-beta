package xxliam.cookieclient.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.render.RoundedRectangle;

import java.util.List;

/**
 * zen watermark/HUD 体系的自绘文字 + 矢量描边基座（cookie 侧适配层）。
 * <p>
 * 文字：zen 的 {@code DrawContext.drawString} 一律以「基线」为 y 语义，而 cookie 的
 * {@link CustomFont} 是「字形盒顶」锚点；换算公式沿用本项目既有约定
 * {@code top = baselineY - ascent / scale}（照搬 opal DropdownRender.baseline 的公式）。
 * {@link #fontAscentHack} 对应 zen {@code GlHelper.getFontAscent} 的居中 hack。
 * <p>
 * 矢量：{@link #drawArc} 用 TRIANGLE_STRIP 粗弧（等价 zen {@code DrawContext.drawArc}），
 * {@link #strokePolyline} 用「分段四边形 + 圆帽/圆关节」复刻 zen {@code drawPath} 的描边路径
 * （AutoPlay 环形进度 + 对勾勾画动画所需）。
 */
public final class ZenHudDraw {

    private ZenHudDraw() {
    }

    // ---------------------------------------------------------------------
    // 字体度量（visual px）
    // ---------------------------------------------------------------------

    public static float ascent(CustomFont font) {
        return (float) font.getFontMetrics().getAscent() / font.getScale();
    }

    public static float descent(CustomFont font) {
        return (float) font.getFontMetrics().getDescent() / font.getScale();
    }

    public static float leading(CustomFont font) {
        return (float) font.getFontMetrics().getLeading() / font.getScale();
    }

    /** capHeight 近似（照搬 zen GlyphMetrics：ascent × 0.7）。 */
    public static float capHeight(CustomFont font) {
        return ascent(font) * 0.7f;
    }

    /** 行盒高 = ascent + descent（对应 zen getLineHeight）。 */
    public static float lineHeight(CustomFont font) {
        return ascent(font) + descent(font);
    }

    /**
     * zen {@code GlHelper.getFontAscent}：居中 hack。
     * 原式 {@code ceil((lineGap - ascent - descent)/2)}，其中 zen ascent 为负
     * （= -ascentAbs），代入即 {@code ceil((leading + ascentAbs - descentAbs)/2)}。
     */
    public static float fontAscentHack(CustomFont font) {
        return (float) Math.ceil((leading(font) + ascent(font) - descent(font)) / 2.0f);
    }

    // ---- zen GlyphMetrics 同符号别名（zen ascent 为负值），供逐字移植原式 ----

    /** zen {@code glyphMetrics.ascent()}：-ascentAbs。 */
    public static float zenAscent(CustomFont font) {
        return -ascent(font);
    }

    /** zen {@code glyphMetrics.descent()}：+descentAbs。 */
    public static float zenDescent(CustomFont font) {
        return descent(font);
    }

    /** zen {@code glyphMetrics.height()}：AWT height / scale（含 leading）。 */
    public static float zenHeight(CustomFont font) {
        return ascent(font) + descent(font) + leading(font);
    }

    /** zen {@code glyphMetrics.capHeight()}：ascentAbs × 0.7。 */
    public static float zenCapHeight(CustomFont font) {
        return capHeight(font);
    }

    /** zen {@code glyphMetrics.getLineHeight()}：descent − ascent = ascentAbs + descentAbs。 */
    public static float zenLineHeight(CustomFont font) {
        return lineHeight(font);
    }

    /** zen {@code glyphMetrics.getLineGap()}：leading。 */
    public static float zenLineGap(CustomFont font) {
        return leading(font);
    }

    // ---------------------------------------------------------------------
    // 文字（baseline 语义，等价 zen DrawContext.drawString）
    // ---------------------------------------------------------------------

    /** 以 baselineY 为基线画纯色文字。 */
    public static void drawBaseline(PoseStack poseStack, CustomFont font, String text, float x, float baselineY, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        font.drawString(poseStack, text, x, baselineY - ascent(font), color);
    }

    /** 以 baselineY 为基线，先后绘制 shadowColor 与主色两层文字（+0.5 阴影偏移）。 */
    public static void drawBaselineTwice(PoseStack poseStack, CustomFont font, String text, float x, float baselineY,
                                         int shadowColor, int mainColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        drawBaseline(poseStack, font, text, x + 0.5f, baselineY + 0.5f, shadowColor);
        drawBaseline(poseStack, font, text, x, baselineY, mainColor);
    }

    /** 阴影色由主色 alpha 推导（黑色 alpha×0.65）的两层文字。 */
    public static void drawBaselineAutoShadow(PoseStack poseStack, CustomFont font, String text, float x, float baselineY, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float alpha = (float) (color >>> 24 & 0xFF) / 255.0f;
        int shadowColor = ((int) (alpha * 0.65f * 255.0f)) << 24;
        drawBaselineTwice(poseStack, font, text, x, baselineY, shadowColor, color);
    }

    /**
     * zen {@code GlHelper.drawTextShadowLegacy}：文字 baseline 在 {@code y + fontAscentHack}。
     */
    public static void drawLegacyShadow(PoseStack poseStack, CustomFont font, String text, float x, float y, int color) {
        drawBaselineAutoShadow(poseStack, font, text, x, y + fontAscentHack(font), color);
    }

    // ---------------------------------------------------------------------
    // 圆角矩形快捷入口（对齐 zen drawRoundedRect）
    // ---------------------------------------------------------------------

    public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        Renderer.drawRoundedRect(poseStack, x, y, width, height, radius, color);
    }

    public static void drawRoundedRect(PoseStack poseStack, RoundedRectangle rect, int color) {
        Renderer.drawRoundedRect(poseStack, rect, color);
    }

    // ---------------------------------------------------------------------
    // 矢量：描边弧（TRIANGLE_STRIP）
    // ---------------------------------------------------------------------

    /**
     * 描边圆弧。x1y1/x2y2 为弧的外接矩形对角；startAngle/sweepAngle 与 zen 一致
     * （0°= 右，正方向屏幕 y 向下旋转）。宽度取 strokeWidth。
     */
    public static void drawArc(PoseStack poseStack, float x1, float y1, float x2, float y2,
                               float startAngle, float sweepAngle, float strokeWidth, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f pose = poseStack.last().pose();
        float centerX = (x1 + x2) * 0.5f;
        float centerY = (y1 + y2) * 0.5f;
        float radius = Math.min(x2 - x1, y2 - y1) * 0.5f;
        float[] colorF = colorToFloats(color);
        float innerRadius = radius - strokeWidth * 0.5f;
        float outerRadius = radius + strokeWidth * 0.5f;
        int segments = 32;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; ++i) {
            float t = (float) i / (float) segments;
            double angle = Math.toRadians(startAngle + sweepAngle * t);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            bufferBuilder.vertex(pose, centerX + cos * outerRadius, centerY + sin * outerRadius, 0.0f)
                    .color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
            bufferBuilder.vertex(pose, centerX + cos * innerRadius, centerY + sin * innerRadius, 0.0f)
                    .color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
        }
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------------
    // 矢量：折线描边（圆帽 + 圆关节；复刻 zen drawPath 的描边分支）
    // ---------------------------------------------------------------------

    /**
     * 按点序绘制粗折线（照搬 zen 单段描边的四边形算法），端点和节点补圆帽/圆关节，
     * 近似 zen STROKE + ROUND cap/join 的视觉。
     */
    public static void strokePolyline(PoseStack poseStack, List<float[]> points, float strokeWidth, int color) {
        if (points == null || points.size() < 2) {
            return;
        }
        float[] colorF = colorToFloats(color);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f pose = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        float half = strokeWidth * 0.5f;
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < points.size() - 1; ++i) {
            float x1 = points.get(i)[0];
            float y1 = points.get(i)[1];
            float x2 = points.get(i + 1)[0];
            float y2 = points.get(i + 1)[1];
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.hypot(dx, dy);
            if (length < 1.0E-4f) {
                continue;
            }
            float nx = -dy / length * half;
            float ny = dx / length * half;
            bufferBuilder.vertex(pose, x1 + nx, y1 + ny, 0.0f).color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
            bufferBuilder.vertex(pose, x1 - nx, y1 - ny, 0.0f).color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
            bufferBuilder.vertex(pose, x2 - nx, y2 - ny, 0.0f).color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
            bufferBuilder.vertex(pose, x2 + nx, y2 + ny, 0.0f).color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
        }
        BufferUploader.drawWithShader(bufferBuilder.end());
        // 节点/端点圆帽（三角扇）
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        emitCircle(bufferBuilder, pose, points.get(0)[0], points.get(0)[1], half, colorF);
        BufferUploader.drawWithShader(bufferBuilder.end());
        for (int i = 1; i < points.size() - 1; ++i) {
            bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            emitCircle(bufferBuilder, pose, points.get(i)[0], points.get(i)[1], half, colorF);
            BufferUploader.drawWithShader(bufferBuilder.end());
        }
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        emitCircle(bufferBuilder, pose, points.get(points.size() - 1)[0], points.get(points.size() - 1)[1], half, colorF);
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    /** 向当前 TRIANGLE_FAN buffer 提交一个实心圆（首顶点为圆心）。 */
    private static void emitCircle(BufferBuilder bufferBuilder, Matrix4f pose, float cx, float cy, float radius, float[] colorF) {
        bufferBuilder.vertex(pose, cx, cy, 0.0f)
                .color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
        int segments = 16;
        for (int i = 0; i <= segments; ++i) {
            double angle = Math.PI * 2.0 * i / segments;
            bufferBuilder.vertex(pose, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 0.0f)
                    .color(colorF[0], colorF[1], colorF[2], colorF[3]).endVertex();
        }
    }

    private static float[] colorToFloats(int color) {
        return new float[]{
                (float) (color >> 16 & 0xFF) / 255.0f,
                (float) (color >> 8 & 0xFF) / 255.0f,
                (float) (color & 0xFF) / 255.0f,
                (float) (color >>> 24 & 0xFF) / 255.0f};
    }
}
