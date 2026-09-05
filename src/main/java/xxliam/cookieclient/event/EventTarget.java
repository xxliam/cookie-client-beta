package xxliam.cookieclient.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注事件监听方法。
 * <p>
 * 用法：
 * <pre>
 * &#64;EventTarget(EventPriority.HIGH)
 * public void onTick(GameTickEvent event) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventTarget {

    int value() default EventPriority.NORMAL;
}
