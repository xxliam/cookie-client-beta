package xxliam.cookieclient.utils.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * 数学工具：钳制、插值、随机数、取整等。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.utils.math.MathUtil}。
 */
public final class MathUtil {

    public static final Random RANDOM = new Random();

    private MathUtil() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 保留指定小数位。decimals==0 时向下取整。 */
    public static double round(double value, int decimals) {
        if (decimals == 0) {
            return Math.floor(value);
        }
        double factor = Math.pow(10.0, decimals);
        return Math.round(value * factor) / factor;
    }

    /** 按步进取整。 */
    public static double snap(double value, double step) {
        double snapped = Math.round(value / step) * step;
        snapped *= 1000.0;
        snapped = (int) snapped;
        return snapped / 1000.0;
    }

    public static double roundDecimal(double value, int decimals) {
        if (decimals < 0) {
            return value;
        }
        BigDecimal bigDecimal = new BigDecimal(value);
        bigDecimal = bigDecimal.setScale(decimals, RoundingMode.HALF_UP);
        return bigDecimal.doubleValue();
    }

    public static double randomInt(int min, int max) {
        return min >= max ? min : RANDOM.nextInt(max - min) + min;
    }

    public static double randomDouble(double min, double max) {
        return min >= max ? min : RANDOM.nextDouble() * (max - min) + min;
    }

    /** 返回 [min, max) 区间的随机 float（对齐 OpenZen 的参数顺序）。 */
    public static float randomFloat(float max, float min) {
        return min >= max ? min : RANDOM.nextFloat() * (max - min) + min;
    }

    public static float clampPitch(float pitch) {
        return clamp(pitch, -90.0f, 90.0f);
    }

    public static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    public static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    public static int lerpColor(int colorA, int colorB, float progress) {
        progress = clamp(progress, 0.0f, 1.0f);
        int alphaA = colorA >> 24 & 0xFF;
        int redA = colorA >> 16 & 0xFF;
        int greenA = colorA >> 8 & 0xFF;
        int blueA = colorA & 0xFF;
        int alphaB = colorB >> 24 & 0xFF;
        int redB = colorB >> 16 & 0xFF;
        int greenB = colorB >> 8 & 0xFF;
        int blueB = colorB & 0xFF;
        int alpha = (int) (alphaA * (1.0f - progress) + alphaB * progress);
        int red = (int) (redA * (1.0f - progress) + redB * progress);
        int green = (int) (greenA * (1.0f - progress) + greenB * progress);
        int blue = (int) (blueA * (1.0f - progress) + blueB * progress);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
