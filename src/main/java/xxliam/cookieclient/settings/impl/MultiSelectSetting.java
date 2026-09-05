package xxliam.cookieclient.settings.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.SettingVisibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 多选设置，从一组选项中勾选任意个（对齐 OpenZen：{@code new MultiSelectSetting("Name", "A", "B").withDefaults("A")}）。
 */
public class MultiSelectSetting extends Setting<List<String>> {

    private final List<String> options;

    public MultiSelectSetting(String name, String... options) {
        super(name, new ArrayList<>());
        this.options = Arrays.asList(options);
    }

    public List<String> getOptions() {
        return options;
    }

    public MultiSelectSetting withDefaults(String... defaults) {
        setValue(new ArrayList<>(Arrays.asList(defaults)));
        return this;
    }

    public MultiSelectSetting withVisibility(SettingVisibility visibility) {
        setVisibility(visibility);
        return this;
    }

    public boolean isSelected(String option) {
        return getValue().contains(option);
    }

    @Override
    public void save(JsonObject jsonObject) {
        JsonArray array = new JsonArray();
        for (String s : getValue()) {
            array.add(s);
        }
        jsonObject.add(getName(), array);
    }

    @Override
    public void load(JsonElement jsonElement) {
        ArrayList<String> list = new ArrayList<>();
        if (jsonElement.isJsonArray()) {
            for (JsonElement e : jsonElement.getAsJsonArray()) {
                list.add(e.getAsString());
            }
        }
        setValue(list);
    }
}
