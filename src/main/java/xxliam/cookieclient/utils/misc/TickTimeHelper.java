package xxliam.cookieclient.utils.misc;

/**
 * 刻计时器（照搬 Naven {@code TickTimeHelper}）。
 * <p>
 * Naven 版是「静态注册表 + 全局 {@code update()}」：所有实例放进一个 static list，由主循环每刻统一自增。
 * cookie 没有全局 tick 广播，改成持有者自己在 {@code onTick()} 里调 {@link #tick()} 推进
 * —— 对单个消费者的语义等价（模块开启期间每刻 +1），且模块关闭时计时冻结（比 Naven 更保守）。
 */
public class TickTimeHelper {

    private int tickPassed = 0;

    /** 每游戏刻推进一次（由持有模块的 {@code onTick()} 调用）。 */
    public void tick() {
        this.tickPassed++;
    }

    /** 距上次 {@link #reset()} 是否已经过 {@code ticks} 刻。 */
    public boolean delay(int ticks) {
        return this.tickPassed >= ticks;
    }

    /** 同上，浮点档（照搬 Naven 的 {@code delay(float)}）。 */
    public boolean delay(float ticks) {
        return (float) this.tickPassed >= ticks;
    }

    public void reset() {
        this.tickPassed = 0;
    }
}
