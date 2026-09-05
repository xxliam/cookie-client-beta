package xxliam.cookieclient.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 设置项基类，泛型化以支持布尔 / 数值 / 枚举等类型。
 * <p>
 * 对齐 OpenZen 的 {@code shit.zen.settings.Setting}：含可见性、变更回调与 JSON 序列化。
 */
public abstract class Setting<T> {

    private String name;
    private T value;
    private SettingVisibility visibility;

    public Setting(String name, T value) {
        this.name = name;
        this.value = value;
        this.visibility = () -> true;
        onInit(value);
    }

    public Setting(String name, T value, SettingVisibility visibility) {
        this.name = name;
        this.value = value;
        this.visibility = visibility;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        T old = this.value;
        this.value = value;
        onChanged(old, value);
    }

    public SettingVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(SettingVisibility visibility) {
        this.visibility = visibility;
    }

    /** 构造后初始化回调。 */
    public void onInit(T value) {
    }

    /** 值变更回调（oldValue, newValue）。 */
    public void onChanged(T oldValue, T newValue) {
    }

    public abstract void save(JsonObject jsonObject);

    public abstract void load(JsonElement jsonElement);
}
