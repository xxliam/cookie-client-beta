package xxliam.cookieclient.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.SettingVisibility;

/**
 * 数值设置，带最小 / 最大 / 步进约束（值类型为 {@link Number}，对齐 OpenZen）。
 */
public class NumberSetting extends Setting<Number> {

    private final Number min;
    private final Number max;
    private final Number step;

    public NumberSetting(String name, Number value, Number min, Number max, Number step) {
        super(name, value);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public NumberSetting(String name, Number value, Number min, Number max, Number step, SettingVisibility visibility) {
        super(name, value, visibility);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public Number getMin() {
        return min;
    }

    public Number getMax() {
        return max;
    }

    public Number getStep() {
        return step;
    }

    @Override
    public void save(JsonObject jsonObject) {
        jsonObject.addProperty(getName(), getValue());
    }

    @Override
    public void load(JsonElement jsonElement) {
        setValue(jsonElement.getAsNumber());
    }
}
