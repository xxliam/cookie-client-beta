package xxliam.cookieclient.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.SettingVisibility;

/**
 * 枚举 / 模式设置，在一组预设值之间切换（对齐 OpenZen：{@code new ModeSetting("Mode", "A", "B").withDefault("A")}）。
 */
public class ModeSetting extends Setting<String> {

    private final String[] modes;

    public ModeSetting(String name, String... modes) {
        super(name, null);
        this.modes = modes;
    }

    public String[] getModes() {
        return modes;
    }

    public ModeSetting withDefault(String value) {
        setValue(value);
        return this;
    }

    public ModeSetting withVisibility(SettingVisibility visibility) {
        setVisibility(visibility);
        return this;
    }

    public boolean is(String value) {
        return getValue() != null && getValue().equals(value);
    }

    @Override
    public void save(JsonObject jsonObject) {
        jsonObject.addProperty(getName(), getValue());
    }

    @Override
    public void load(JsonElement jsonElement) {
        setValue(jsonElement.getAsString());
    }
}
