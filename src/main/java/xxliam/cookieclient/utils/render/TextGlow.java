package xxliam.cookieclient.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.Renderer;

import java.awt.Color;

/**
 * 面板背景与「发光文字」工具，逐字搬运自 OpenZen 的 {@code shit.zen.render.TextGlow}。
 * <p>
 * <b>注意（沿用 zen 的真实行为，勿自行"补上"发光）</b>：
 * {@link #drawGlowText} 在 zen 源码里忽略 {@code glowColor} 与 {@code radius}，
 * 只是普通文字绘制——它的名字有误导性，但 Panel 风格 ClickGUI 里 25 处调用点的观感
 * 就是「普通文字」，照搬即可。
 */
public final class TextGlow {

    private TextGlow() {
    }

    /**
     * 面板/浮层底：两层同尺寸圆角矩形叠加 —— 深灰底 {@code (24,24,24)} + 低 alpha 白高光，
     * 形成 zen 那种「磨砂玻璃」观感。
     */
    public static void drawBackground(PoseStack poseStack, float x, float y, float width, float height, float radius, float alpha) {
        int darkAlpha = clampAlpha(150.0f * alpha);
        int lightAlpha = clampAlpha(35.0f * alpha);
        Renderer.drawRoundedRect(poseStack, x, y, width, height, radius, new Color(24, 24, 24, darkAlpha).getRGB());
        Renderer.drawRoundedRect(poseStack, x, y, width, height, radius, new Color(255, 255, 255, lightAlpha).getRGB());
    }

    /**
     * 画一段文字并返回下一个光标 X（zen 原名 drawGlowText；参数 glowColor / radius 与 zen 一样不使用）。
     */
    public static float drawGlowText(PoseStack poseStack, CustomFont font, String text, float x, float y,
                                     int color, int glowColor, float radius) {
        return RenderHelper.drawText(poseStack, font, text, x, y, color);
    }

    private static int clampAlpha(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
