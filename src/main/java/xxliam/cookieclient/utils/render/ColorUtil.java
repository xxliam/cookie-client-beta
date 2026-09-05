package xxliam.cookieclient.utils.render;

import net.minecraft.world.entity.player.Player;

import java.awt.Color;

/**
 * 颜色工具：ARGB 打包、透明度缩放、颜色插值、彩虹色等。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.utils.render.ColorUtil}。
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    public static Color getPlayerColor(Player player) {
        int hash = player.getName().getString().hashCode();
        int red = (hash & 0xFF0000) >> 16;
        int green = (hash & 0xFF00) >> 8;
        int blue = hash & 0xFF;
        return new Color(red, green, blue);
    }

    /** 在 colorA / colorB 之间循环渐变。 */
    public static int animateColor(int colorA, int colorB, double progress) {
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return interpolateColor(colorA, colorB, progress);
    }

    /** 随时间自动渐变，offsetMs 为相位偏移。 */
    public static int animateColorOffset(int colorA, int colorB, long offsetMs) {
        return animateColor(colorA, colorB, (double) ((System.currentTimeMillis() + offsetMs) % 4000L) / 2000.0);
    }

    public static int interpolateColor(int colorA, int colorB, double progress) {
        double inverse = 1.0 - progress;
        int red = (int) ((colorA >> 16 & 0xFF) * inverse + (colorB >> 16 & 0xFF) * progress);
        int green = (int) ((colorA >> 8 & 0xFF) * inverse + (colorB >> 8 & 0xFF) * progress);
        int blue = (int) ((colorA & 0xFF) * inverse + (colorB & 0xFF) * progress);
        int alpha = (int) ((colorA >>> 24 & 0xFF) * inverse + (colorB >>> 24 & 0xFF) * progress);
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    public static Color getRainbowColor(int speed, int offset) {
        int hueDegrees = (int) ((System.currentTimeMillis() / (long) speed + offset) % 360L);
        float hue = (float) hueDegrees / 360.0f;
        return new Color(Color.HSBtoRGB(hue, 0.5f, 1.0f));
    }

    public static int fromRGB(int red, int green, int blue) {
        return fromARGB(red, green, blue, 255);
    }

    public static int fromARGB(int red, int green, int blue, int alpha) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    /** 将颜色的 alpha 乘以一个 [0,1] 因子。 */
    public static int withAlpha(int color, float alphaScale) {
        alphaScale = Math.min(1.0f, Math.max(0.0f, alphaScale));
        int rgb = color & 0xFFFFFF;
        int alpha = (int) ((color >>> 24 & 0xFF) * alphaScale);
        return (alpha << 24) | rgb;
    }

    public static Color withAlphaColor(Color color, float alphaScale) {
        alphaScale = Math.min(1.0f, Math.max(0.0f, alphaScale));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * alphaScale));
    }

    public static int getAlpha(int color) {
        return color >>> 24 & 0xFF;
    }

    public static int getRed(int color) {
        return color >> 16 & 0xFF;
    }

    public static int getGreen(int color) {
        return color >> 8 & 0xFF;
    }

    public static int getBlue(int color) {
        return color & 0xFF;
    }

    // ---- 以下方法语义/公式照搬 OpenOpal ColorUtility ----

    public static int getShadowColor(final int color) {
        return (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
    }

    public static int[] hexToRGBA(final int hex) {
        return new int[]{(hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF, (hex >> 24) & 0xFF};
    }

    public static int rgbaToHex(final int red, final int green, final int blue, final int alpha) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** 变暗：rgb 各通道 × (1-factor)，alpha 保留。照搬 opal。 */
    public static int darker(final int color, final float factor) {
        final float f = 1 - factor;
        final int r = (int) ((color >> 16 & 0xFF) * f);
        final int g = (int) ((color >> 8 & 0xFF) * f);
        final int b = (int) ((color & 0xFF) * f);
        final int a = color >> 24 & 0xFF;
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF) | ((a & 0xFF) << 24);
    }

    /** 变亮：rgb 各通道 ÷(1-factor) 并夹取 255，alpha 保留。照搬 opal。 */
    public static int brighter(final int color, final float factor) {
        final float f = 1 / (1 - factor);
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        final int a = (color >> 24) & 0xFF;

        if (r == 0 && g == 0 && b == 0) {
            int grey = (int) (1.0 / (1.0 - factor));
            return ((a & 0xFF) << 24) | ((grey & 0xFF) << 16) | ((grey & 0xFF) << 8) | (grey & 0xFF);
        }

        int minBrightness = (int) (1.0 / (1.0 - factor));
        int newR = r > 0 && r < minBrightness ? minBrightness : r;
        int newG = g > 0 && g < minBrightness ? minBrightness : g;
        int newB = b > 0 && b < minBrightness ? minBrightness : b;

        newR = Math.min((int) (newR * f), 255);
        newG = Math.min((int) (newG * f), 255);
        newB = Math.min((int) (newB * f), 255);

        return ((a & 0xFF) << 24) | ((newR & 0xFF) << 16) | ((newG & 0xFF) << 8) | (newB & 0xFF);
    }

    /** 重设 alpha 为 opacityFactor×255（忽略原 alpha）。照搬 opal。 */
    public static int applyOpacity(final int color, float opacityFactor) {
        opacityFactor = Math.min(1, Math.max(0, opacityFactor));
        final int[] rgba = hexToRGBA(color);
        return rgbaToHex(rgba[0], rgba[1], rgba[2], (int) (opacityFactor * 255.F));
    }

    /** 重设 alpha 为 0~255。照搬 opal。 */
    public static int applyOpacity(final int color, int opacity) {
        opacity = Math.min(255, Math.max(0, opacity));
        final int[] rgba = hexToRGBA(color);
        return rgbaToHex(rgba[0], rgba[1], rgba[2], opacity);
    }

    public static int interpolateColors(final int color1, final int color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));
        final int[] c1 = hexToRGBA(color1);
        final int[] c2 = hexToRGBA(color2);
        return rgbaToHex(
                (int) (c1[0] + (c2[0] - c1[0]) * amount),
                (int) (c1[1] + (c2[1] - c1[1]) * amount),
                (int) (c1[2] + (c2[2] - c1[2]) * amount),
                (int) (c1[3] + (c2[3] - c1[3]) * amount));
    }

    /** 彩虹色（speed 毫秒每整圈、index 相位、saturation/brightness）。照搬 opal。 */
    public static int rainbow(final int speed, final int index, final float saturation, final float brightness) {
        final int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        return Color.HSBtoRGB(angle / 360f, saturation, brightness);
    }

    /** 在两色间来回摆动渐变（用于模块列表逐行取色）。照搬 opal。 */
    public static int interpolateColorsBackAndForth(final int speed, final int index, final int startColor, final int endColor) {
        int angle = (int) (((System.currentTimeMillis()) / speed - index) % 360);
        angle = (angle >= 180 ? 360 - angle : angle) * 2;
        return interpolateColors(startColor, endColor, angle / 360f);
    }
}
