package xxliam.cookieclient.event;

/**
 * 可取消事件的基类。需要拦截 / 修改原逻辑的事件继承此类。
 */
public abstract class AbstractCancellable extends Event implements Cancellable {

    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
