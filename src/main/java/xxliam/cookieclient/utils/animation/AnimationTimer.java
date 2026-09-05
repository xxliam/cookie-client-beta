package xxliam.cookieclient.utils.animation;

import xxliam.cookieclient.utils.math.Easing;

/**
 * 动画计时器基类：记录起止时间/值，按缓动函数插值。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.utils.animation.AnimationTimer}。
 */
public abstract class AnimationTimer {

    private double currentValue;
    private long startTime;
    private double duration;
    private double fromValue;
    private double toValue;
    private Easing easing;
    private boolean debug;

    public boolean tick() {
        boolean animating = isAnimating();
        if (animating) {
            currentValue = lerp(getFromValue(), getToValue(), getEasing().ease(getProgress()));
        } else {
            startTime = 0L;
            currentValue = getToValue();
        }
        return animating;
    }

    public boolean isAnimating() {
        return !isDone();
    }

    public boolean isDone() {
        return getProgress() >= 1.0;
    }

    public double getProgress() {
        return (double) (System.currentTimeMillis() - getStartTime()) / getDuration();
    }

    public double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    public void setCurrentValue(double currentValue) {
        this.currentValue = currentValue;
    }

    public float getValueF() {
        return (float) currentValue;
    }

    public int getValueI() {
        return (int) currentValue;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public double getFromValue() {
        return fromValue;
    }

    public void setFromValue(double fromValue) {
        this.fromValue = fromValue;
    }

    public double getToValue() {
        return toValue;
    }

    public void setToValue(double toValue) {
        this.toValue = toValue;
    }

    public Easing getEasing() {
        return easing;
    }

    public void setEasing(Easing easing) {
        this.easing = easing;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
