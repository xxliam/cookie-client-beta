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
        if (isAnimating() && (target == getFromValue() || target == getToValue() || target == getValueF())) {
            return;
        }
        setEasing(easing);
        setDuration(duration * 1000.0);
        setStartTime(System.currentTimeMillis());
        setFromValue(getValueF());
        setToValue(target);
    }
}
