package xxliam.cookieclient.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.SettingVisibility;

/**
 * 颜色设置：以 ARGB int（Number 承载，兼容现有 ValuesConfig 泛型读取）存储。
 * <p>
 * 语义对齐 opal 的 {@code ColorProperty}：默认 RGBA 顺序 {@code 0xAARRGGBB}。
 */
public class ColorSetting extends Setting<Number> {

    public ColorSetting(String name, int argb) {
        super(name, argb);
    }

    public ColorSetting(String name, int argb, SettingVisibility visibility) {
        super(name, argb, visibility);
    }

    public int getColor() {
        return getValue().intValue();
    }

    @Override
    public void save(JsonObject jsonObject) {
        jsonObject.addProperty(getName(), getColor());
    }

    @Override
    public void load(JsonElement jsonElement) {
        setValue(jsonElement.getAsNumber().intValue());
    }
}
