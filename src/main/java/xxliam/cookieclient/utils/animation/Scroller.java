package xxliam.cookieclient.utils.animation;

import xxliam.cookieclient.utils.math.Easings;

/**
 * 滚动容器：内容偏移的累加 / 钳制 / 缓动（照搬 OpenOpal {@code wtf.opal.utility.render.Scroller}）。
 * <p>
 * 内部 {@code value} 是<b>内容偏移量，恒为负</b>（0 = 贴顶、{@code -maxOffset} = 滚到底），
 * 消费方按 {@code 内容 y = 基线 + getValue()} 使用即可（opal 就是 {@code currentY + scrollOffset}）。
 * 每格滚轮 = 50px。
 * <p>
 * 两个入口各司其职：
 * <ul>
 *   <li>{@link #addScroll(double, float)} —— 滚轮事件里调，累加一格后钳制；</li>
 *   <li>{@link #onScroll(float)} —— <b>每帧渲染时</b>调（照搬 opal 在 {@code render()} 末尾调它的做法）：
 *       重新钳制并让动画对齐。这一步是必要的，内容高度变化（模块展开 / 折叠）导致 maxOffset 变小时，
 *       偏移会自动收回，不会卡在越界位置。</li>
 * </ul>
 * 缓动与时长照搬 opal：{@code EASE_OUT_EXPO} / 250ms。
 */
public final class Scroller {

    /** 动画时长（秒）——opal {@code new Animation(Easing.EASE_OUT_EXPO, 250)}。 */
    private static final double DURATION_SECONDS = 0.25;
    /** 每格滚轮的像素位移——opal {@code verticalScroll * 50}。 */
    private static final double PIXELS_PER_NOTCH = 50.0;

    private final SmoothAnimationTimer animation = new SmoothAnimationTimer();
    /** 目标偏移（≤ 0）；对外暴露的是 animation 的当前值。 */
    private float value;

    /** 当前内容偏移（动画值，≤ 0；0 = 贴顶）。 */
    public float getValue() {
        return animation.getValueF();
    }

    /** 每帧渲染时调用：把偏移钳回 {@code [-maxOffset, 0]} 并把动画对齐到该值。 */
    public void onScroll(float maxOffset) {
        this.value = Math.min(0.0f, Math.max(-maxOffset, this.value));
        animate();
    }

    /** 滚轮事件：累加一格滚动量后钳制并动画。 */
    public void addScroll(double verticalScroll, float maxOffset) {
        this.value += (float) (verticalScroll * PIXELS_PER_NOTCH);
        // 先钳下界（不越过底部）再钳上界（不越过顶部），与 opal 的先后顺序一致
        this.value = Math.max(-maxOffset, this.value);
        this.value = Math.min(0.0f, this.value);
        animate();
    }

    private void animate() {
        animation.animate(this.value, DURATION_SECONDS, Easings.EASE_OUT_EXPO);
        animation.tick();
    }
}
