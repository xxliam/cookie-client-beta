package xxliam.cookieclient.event;

/**
 * 可取消事件接口，对应 OpenZen 的 {@code shit.zen.event.Cancellable}。
 */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
