package xxliam.cookieclient.utils.animation;

import xxliam.cookieclient.utils.math.Easing;
import xxliam.cookieclient.utils.math.Easings;

/**
 * 平滑动画计时器：{@code animate(target, duration, easing)} 从当前值平滑过渡到目标值。
 */
public class SmoothAnimationTimer extends AnimationTimer {

    public void animate(double target, double duration) {
        animate(target, duration, Easings.EASE_OUT_QUAD);
    }

    public void animate(double target, double duration, Easing easing) {
        // 目标未变时不重置计时器（opal Animation.run 同语义）：
        // 若这里在动画播完后仍重置 startTime，tick() 将永远走不到
        // 「progress>=1 → currentValue=toValue」的吸附分支，值只能按
        // 残差×(1-ease) 逐周期渐近逼近目标、长期停在 1e-9 量级的正数上——
        // 于是「值 == 0」这类关闭判定永不成立、只能靠超时兜底。
        if (target == getToValue()) {
            return;
        }
        setEasing(easing);
        setDuration(duration * 1000.0);
        setStartTime(System.currentTimeMillis());
        setFromValue(getValueF());
        setToValue(target);
    }

    /**
     * 立即把当前值 / 目标值置为 {@code value} 并结束进行中的动画（对应
     * {@link SpringAnimation#reset(float)}）：之后的 {@link #animate} 会以它为起点。
     * <p>
     * 用于「先归零再播入场动画」或「动画途中把值同步到外部计算结果」的场合。
     */
    public void reset(double value) {
        setStartTime(0L);
        setDuration(1.0); // 避免 progress 出现 0/0；startTime=0 + duration=1 ⇒ progress 极大 ⇒ 视为已完成
        setFromValue(value);
        setToValue(value);
        setCurrentValue(value);
    }
}
