package xxliam.cookieclient.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.SettingVisibility;

/**
 * 布尔开关设置。
 */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, Boolean value) {
        super(name, value);
    }

    public BooleanSetting(String name, Boolean value, SettingVisibility visibility) {
        super(name, value, visibility);
    }

    @Override
    public void save(JsonObject jsonObject) {
        jsonObject.addProperty(getName(), getValue());
    }

    @Override
    public void load(JsonElement jsonElement) {
        setValue(jsonElement.getAsBoolean());
    }
}
