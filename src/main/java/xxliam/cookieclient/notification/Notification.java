package xxliam.cookieclient.notification;

/**
 * 单条通知（照搬 OpenOpal {@code Notification}）。
 * <p>
 * 计时以创建时刻为起点，{@link #hasExpired()} 供渲染端判定滑出/移除时机。
 */
public final class Notification {

    private final long startTime = System.currentTimeMillis();
    private final NotificationType type;
    private final String title;
    private final String description;
    private final int duration;

    Notification(final NotificationType type, final String title, final String description, final int duration) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.duration = duration;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getDuration() {
        return duration;
    }

    /** 自创建以来经过的毫秒数。 */
    public long getTime() {
        return System.currentTimeMillis() - startTime;
    }

    /** 是否已到展示时长（对应 opal hasTimeElapsed(duration, false)）。 */
    public boolean hasExpired() {
        return getTime() >= duration;
    }
}
