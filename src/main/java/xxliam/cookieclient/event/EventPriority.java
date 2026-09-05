package xxliam.cookieclient.event;

/**
 * 事件优先级常量。数值越小越先执行。
 */
public final class EventPriority {

    public static final int HIGHEST = 0;
    public static final int HIGH = 1;
    public static final int NORMAL = 2;
    public static final int LOW = 3;
    public static final int LOWEST = 4;

    private EventPriority() {
    }
}
