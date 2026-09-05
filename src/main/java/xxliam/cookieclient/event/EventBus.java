package xxliam.cookieclient.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件总线：注册监听器并按优先级分发事件。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.event.EventBus}，实现简化：
 * 通过 {@link EventTarget} 注解 + 反射收集监听方法。
 */
public class EventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);

    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<ListenerEntry>> listeners = new ConcurrentHashMap<>();

    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个监听器对象（其带有 @EventTarget 注解的方法都会被收集）。
     */
    public void register(Object listener) {
        for (Method method : listener.getClass().getMethods()) {
            if (!method.isAnnotationPresent(EventTarget.class)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !Event.class.isAssignableFrom(params[0])) {
                LOGGER.warn("Invalid event handler: {}#{}", listener.getClass().getName(), method.getName());
                continue;
            }
            int priority = method.getAnnotation(EventTarget.class).value();
            listeners.computeIfAbsent(params[0], k -> new ArrayList<>())
                    .add(new ListenerEntry(listener, method, priority));
        }
        listeners.values().forEach(list -> list.sort(Comparator.comparingInt(ListenerEntry::getPriority)));
    }

    /**
     * 注销一个监听器对象。
     */
    public void unregister(Object listener) {
        listeners.values().forEach(list -> list.removeIf(entry -> entry.listener == listener));
    }

    /**
     * 分发事件。返回同一事件对象，便于链式使用。
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {
        List<ListenerEntry> entries = listeners.get(event.getClass());
        if (entries == null) {
            return event;
        }
        for (ListenerEntry entry : entries) {
            if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
                break;
            }
            try {
                entry.method.setAccessible(true);
                entry.method.invoke(entry.listener, event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.error("Failed to dispatch event {} to {}", event.getClass().getSimpleName(), entry.listener.getClass().getName(), e);
            }
        }
        return event;
    }

    private static class ListenerEntry {
        final Object listener;
        final Method method;
        final int priority;

        ListenerEntry(Object listener, Method method, int priority) {
            this.listener = listener;
            this.method = method;
            this.priority = priority;
        }

        int getPriority() {
            return priority;
        }
    }
}
