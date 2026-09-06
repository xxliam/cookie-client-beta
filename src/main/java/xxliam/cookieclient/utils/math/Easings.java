package xxliam.cookieclient.utils.math;

/**
 * 预设缓动函数集合，仿 OpenZen 的 {@code shit.zen.utils.math.Easings}。
 */
public final class Easings {

    public static final Easing BACK_OUT = t -> 1.0 + 2.70158 * Math.pow(t - 1.0, 3.0) + 1.70158 * Math.pow(t - 1.0, 2.0);
    /** 线性（复刻 OpenOpal {@code Easing.LINEAR}，Opal 数值滑杆拖动动画用它）。 */
    public static final Easing LINEAR = t -> t;
    /** Opal {@code Easing.DECELERATE}（1-(x-1)^2）在 cookie 侧即 EASE_OUT_QUAD，见下方。 */
    public static final Easing EASE_OUT_QUAD = t -> 1.0 - (t - 1.0) * (t - 1.0);
    /** 复刻 OpenOpal 的 {@code Easing.EASE_OUT_EXPO}（x == 1 ? 1 : 1 - 2^(-10x)）。 */
    public static final Easing EASE_OUT_EXPO = t -> t == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
    public static final Easing EASE_OUT_POW2 = easeOut(2);
    public static final Easing EASE_IN_POW3 = easeIn(3);
    public static final Easing EASE_OUT_POW3 = easeOut(3);
    public static final Easing EASE_OUT_POW4 = easeOut(4);
    public static final Easing EASE_OUT_POW5 = easeOut(5);
    public static final Easing EASE_OUT_SINE = t -> Math.sin(t * Math.PI / 2.0);
    /** 复刻 OpenOpal 的 {@code Easing.EASE_IN_OUT_CUBIC}。 */
    public static final Easing EASE_IN_OUT_CUBIC = t -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
    public static final Easing EASE_OUT_ELASTIC = t -> {
        if (t == 0.0 || t == 1.0) {
            return t;
        }
        return Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * 2.0943951023931953) + 1.0;
    };
    public static final Easing EASE_OUT_BOUNCE = t -> {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1.0 / d1) {
            return n1 * Math.pow(t, 2.0);
        }
        if (t < 2.0 / d1) {
            return n1 * Math.pow(t - 1.5 / d1, 2.0) + 0.75;
        }
        if (t < 2.5 / d1) {
            return n1 * Math.pow(t - 2.25 / d1, 2.0) + 0.9375;
        }
        return n1 * Math.pow(t - 2.625 / d1, 2.0) + 0.984375;
    };

    private Easings() {
    }

    public static Easing easeIn(double power) {
        return t -> Math.pow(t, power);
    }

    public static Easing easeIn(int power) {
        return easeIn((double) power);
    }

    public static Easing easeOut(double power) {
        return t -> 1.0 - Math.pow(1.0 - t, power);
    }

    public static Easing easeOut(int power) {
        return easeOut((double) power);
    }

    public static Easing easeInOut(double power) {
        return t -> {
            if (t < 0.5) {
                return Math.pow(2.0, power - 1.0) * Math.pow(t, power);
            }
            return 1.0 - Math.pow(-2.0 * t + 2.0, power) / 2.0;
        };
    }
}
