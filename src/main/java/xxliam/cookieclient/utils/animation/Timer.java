package xxliam.cookieclient.utils.animation;

/**
 * 毫秒计时器，用于 CPS / 冷却等间隔控制。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.utils.animation.Timer}。
 */
public class Timer {

    private long lastTime;

    public Timer() {
        reset();
    }

    public void reset() {
        lastTime = System.currentTimeMillis();
    }

    public long getElapsedMs() {
        return System.currentTimeMillis() - lastTime;
    }

    public boolean passedMs(long ms) {
        return getElapsedMs() >= ms;
    }
}
