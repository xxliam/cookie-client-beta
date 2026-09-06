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
        // DropdownClickGui 的关闭判定（值==0）因此永不成立、只能靠超时兜底。
        if (target == getToValue()) {
            return;
        }
        setEasing(easing);
        setDuration(duration * 1000.0);
        setStartTime(System.currentTimeMillis());
        setFromValue(getValueF());
        setToValue(target);
    }
}
