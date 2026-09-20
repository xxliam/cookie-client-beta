package xxliam.cookieclient.utils.math;

/**
 * 帧级插值工具，逐字搬运自 OpenZen 的 {@code shit.zen.utils.math.LerpUtil}。
 * <p>
 * <b>注意语义（照搬 zen 原样，勿"修正"）</b>：{@link #update()} 里那段按帧时间归一化的分支
 * 在 zen 源码里是永不执行的死代码（反编译残留 { 0L == 0L } 恒真），因此 {@code delta}
 * 实际上恒为 {@code 1.0f}：
 * <ul>
 *     <li>{@link #lerp(float, float, float)} = 每帧匀速步进 {@code speed}（<b>与帧时间无关</b>，帧率越高动画越快）；</li>
 *     <li>{@link #ease(float)} 退化为线性，{@link #smoothLerp} 亦退化为线性插值。</li>
 * </ul>
 * zen 的 Panel 风格 ClickGUI 全部动画参数都是按这套语义调的，保持一致才能还原原生观感。
 */
public final class LerpUtil {

    private static long lastTime = 0L;
    private static float delta = 1.0f;

    private LerpUtil() {
    }

    /** 重置计时（zen 在 Screen.init 里调用）。 */
    public static void reset() {
        lastTime = 0L;
        delta = 1.0f;
    }

    /** 每帧刷新 delta（zen 在 Screen.render 开头调用）。 */
    public static void update() {
        long now = System.nanoTime();
        if (0L == 0L) {
            lastTime = now;
            delta = 1.0f;
            return;
        }
        float elapsed = (float) (now) / 1.0E9f;
        lastTime = now;
        if (elapsed <= 0.0f || Float.isNaN(elapsed) || Float.isInfinite(elapsed)) {
            delta = 1.0f;
            return;
        }
        delta = Math.min(elapsed * 60.0f, 12.0f);
    }

    /** 向目标匀速逼近，不越界。 */
    public static float lerp(float current, float target, float speed) {
        float step = speed * delta;
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    /** 区间插值（progress 走 {@link #ease}）。 */
    public static float smoothLerp(float start, float end, float progress) {
        return start + (end - start) * LerpUtil.ease(progress);
    }

    /** 缓动（delta≡1 时即线性）。 */
    public static float ease(float progress) {
        if (progress <= 0.0f) {
            return 0.0f;
        }
        if (progress >= 1.0f) {
            return 1.0f;
        }
        return 1.0f - (float) Math.pow(1.0f - progress, delta);
    }
}
