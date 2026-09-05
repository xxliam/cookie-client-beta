package xxliam.cookieclient.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知管理器（照搬 OpenOpal {@code NotificationManager}）：持有当前展示中的通知列表，
 * 通过 {@link #builder(NotificationType)} 链式发布。
 */
public final class NotificationManager {

    private final List<Notification> notifications = new ArrayList<>();

    public List<Notification> getNotifications() {
        return notifications;
    }

    public NotificationBuilder builder(final NotificationType type) {
        return new NotificationBuilder(this, type);
    }

    /** 便捷发布入口。 */
    public Notification publish(final NotificationType type, final String title, final String description, final int duration) {
        Notification notification = new Notification(type, title, description, duration);
        notifications.add(notification);
        return notification;
    }

    public void remove(final Notification notification) {
        notifications.remove(notification);
    }

    /** 链式构造器（照搬 opal：title 默认 "Notification"、duration 默认 2000ms）。 */
    public static final class NotificationBuilder {

        private final NotificationManager dispatcher;
        private final NotificationType type;
        private String title;
        private String description;
        private int duration;

        private NotificationBuilder(final NotificationManager dispatcher, final NotificationType type) {
            this.dispatcher = dispatcher;
            this.type = type;
            this.title = "Notification";
            this.duration = 2000;
        }

        public NotificationBuilder title(final String title) {
            this.title = title;
            return this;
        }

        public NotificationBuilder description(final String description) {
            this.description = description;
            return this;
        }

        public NotificationBuilder duration(final int duration) {
            this.duration = duration;
            return this;
        }

        public Notification buildAndPublish() {
            return dispatcher.publish(type, title, description, duration);
        }
    }
}
