package xxliam.cookieclient.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import xxliam.cookieclient.render.FontMetricsImpl;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.render.CustomFont;

import java.awt.FontMetrics;

/**
 * 渲染辅助：PoseStack 缩放/旋转、shader 颜色、自定义字体的度量与排版。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.utils.render.RenderHelper}；文字部分搬运自 OpenZen 的
 * {@code shit.zen.render.GlHelper}（drawText / getStringWidth / getFontAscent / drawTextCentered /
 * drawPlayerHeadRounded），把 {@code FontRenderer} 换成 cookie 的 {@link CustomFont}。
 */
public final class RenderHelper {

    private RenderHelper() {
    }

    /** 以 (pivotX, pivotY) 为轴心缩放。 */
    public static void pushScaleAround(PoseStack poseStack, float pivotX, float pivotY, float scale) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0f);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-pivotX, -pivotY, 0.0f);
    }

    /** 以 (pivotX, pivotY) 为轴心旋转。 */
    public static void pushRotateAround(PoseStack poseStack, float pivotX, float pivotY, float angleDegrees) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angleDegrees));
        poseStack.translate(-pivotX, -pivotY, 0.0f);
    }

    public static void popPose(PoseStack poseStack) {
        poseStack.popPose();
    }

    public static void resetShaderColor() {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void setShaderColor(int color) {
        RenderSystem.setShaderColor(
                (float) ColorUtil.getRed(color) / 255.0f,
                (float) ColorUtil.getGreen(color) / 255.0f,
                (float) ColorUtil.getBlue(color) / 255.0f,
                (float) ColorUtil.getAlpha(color) / 255.0f);
    }

    // ---------------------------------------------------------------------
    // 文字度量 / 排版（照搬 zen GlHelper 的公式）
    // ---------------------------------------------------------------------

    /** 字符串宽度（等价 zen {@code GlHelper.getStringWidth}）。 */
    public static float getStringWidth(CustomFont font, String text) {
        if (font == null || text == null || text.isEmpty()) {
            return 0.0f;
        }
        return font.getStringWidth(CustomFont.stripFormatting(text));
    }

    /**
     * 字形盒顶部到"视觉居中基线"的偏移，等价 zen {@code GlHelper.getFontAscent}。
     * <p>
     * zen 公式：{@code ascent = (metrics.getLineGap() - metrics.ascent() - metrics.descent) / 2}，
     * 代入其 GlyphMetrics 定义（ascent = -fm.getAscent()/scale、descent = fm.getDescent()/scale、
     * height 字段装的是 fm.getHeight()/scale）化简为 {@code (height - 2 * descent) / 2}，再向上取整。
     * 这里按同一化简式用 cookie 的 {@link CustomFont#getFontHeight()} 与 AWT 度量复现。
     */
    public static float getFontAscent(CustomFont font) {
        if (font == null) {
            return 0.0f;
        }
        FontMetrics metrics = font.getFontMetrics();
        int scale = font.getScale();
        if (scale <= 0) {
            scale = 1;
        }
        float descent = (float) metrics.getDescent() / (float) scale;
        float ascent = (font.getFontHeight() - 2.0f * descent) / 2.0f;
        return (float) Math.ceil(ascent);
    }

    /** 大写字母高度（等价 zen {@code font.getMetrics().capHeight()}：ascent × 0.7 / scale）。 */
    public static float getCapHeight(CustomFont font) {
        if (font == null) {
            return 0.0f;
        }
        FontMetricsImpl metrics = font.getFontMetrics();
        int scale = font.getScale();
        if (scale <= 0) {
            scale = 1;
        }
        return (float) metrics.getAscent() * 0.7f / (float) scale;
    }

    /**
     * 画一行文字，返回下一个光标 X（等价 zen {@code GlHelper.drawText}）。
     * <p>
     * {@code y} 是字形盒顶；内部按 {@link #getFontAscent(CustomFont)} 推出基线再交给
     * {@link CustomFont#drawString}（后者把传入 y 当字形整盒顶）。
     */
    public static float drawText(PoseStack poseStack, CustomFont font, String text, float x, float y, int color) {
        if (font == null || text == null || text.isEmpty()) {
            return x;
        }
        float baselineY = y + getFontAscent(font);
        font.drawString(poseStack, text, x, baselineY, color);
        return x + getStringWidth(font, text);
    }

    /** 带阴影（offset 0.5/0.5，黑色 × 0.65 alpha），等价 zen {@code drawTextShadowLegacy}。 */
    public static float drawTextWithShadow(PoseStack poseStack, CustomFont font, String text, float x, float y, int color) {
        if (font == null || text == null || text.isEmpty()) {
            return x;
        }
        int shadow = ColorUtil.fromARGB(0, 0, 0, (int) (ColorUtil.getAlpha(color) * 0.65f));
        drawText(poseStack, font, text, x + 0.5f, y + 0.5f, shadow);
        return drawText(poseStack, font, text, x, y, color);
    }

    /** 以 (centerX, centerY) 为中心的居中文字，等价 zen {@code drawTextCentered}。 */
    public static float drawTextCentered(PoseStack poseStack, CustomFont font, float centerX, float centerY, String text, int color) {
        if (font == null || text == null || text.isEmpty()) {
            return centerX;
        }
        float x = centerX - getStringWidth(font, text) / 2.0f;
        float y = centerY - font.getFontHeight() / 2.0f;
        return drawText(poseStack, font, text, x, y, color);
    }

    /**
     * 圆角玩家头像（等价 zen {@code GlHelper.drawPlayerHeadRounded}）。
     * <p>
     * 皮肤贴图 UV（1.8 格式）：脸区域 {@code (0.125,0.125)-(0.25,0.25)}，
     * 头部覆盖层（帽子）{@code (0.625,0.125)-(0.75,0.25)}；两层用同一圆角裁切依次绘制，
     * 受击时再叠一层红色圆角矩形（alpha 随 hurtTime 衰减）。
     */
    public static void drawPlayerHeadRounded(PoseStack poseStack, AbstractClientPlayer player,
                                             float x, float y, float width, float height, float alpha, float radius) {
        if (player == null) {
            return;
        }
        ResourceLocation textureLocation = player.getSkinTextureLocation();
        if (textureLocation == null) {
            return;
        }
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureLocation);
        if (texture == null) {
            return;
        }
        int glId = texture.getId();
        int packedColor = (int) Math.max(0.0f, Math.min(255.0f, alpha * 255.0f)) << 24 | 0xFFFFFF;

        Renderer.drawRoundedTexture(poseStack, glId, x, y, width, height, radius, 0.125f, 0.125f, 0.25f, 0.25f, packedColor);
        Renderer.drawRoundedTexture(poseStack, glId, x, y, width, height, radius, 0.625f, 0.125f, 0.75f, 0.25f, packedColor);

        if (player.hurtTime > 0) {
            int hurtColor = ColorUtil.fromARGB(255, 0, 0, (int) (player.hurtTime * 18 * alpha));
            Renderer.drawRoundedRect(poseStack, x, y, width, height, radius, hurtColor);
        }
    }
}

