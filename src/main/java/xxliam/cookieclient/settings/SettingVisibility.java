package xxliam.cookieclient.settings;

/**
 * 设置项可见性条件。
 */
@FunctionalInterface
public interface SettingVisibility {
    boolean displayable();
}
