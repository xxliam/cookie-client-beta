package xxliam.cookieclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.Setting;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置值配置：保存每个模块所有 Setting 的取值（values.json）。
 * <p>
 * 格式：{ "ModuleName": { "SettingName": value, ... }, ... }
 */
public class ValuesConfig extends Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ValuesConfig() {
        super("values.json");
    }

    public void load(List<Module> modules) {
        if (!Files.exists(getPath())) {
            return;
        }
        try {
            Map<String, Map<String, Object>> data = GSON.fromJson(
                    Files.readString(getPath()),
                    new TypeToken<Map<String, Map<String, Object>>>() {
                    }.getType());
            if (data == null) {
                return;
            }
            for (Module module : modules) {
                Map<String, Object> values = data.get(module.getName());
                if (values == null) {
                    continue;
                }
                for (Setting<?> setting : module.getSettings()) {
                    Object raw = values.get(setting.getName());
                    if (raw == null) {
                        continue;
                    }
                    applyValue(setting, raw);
                }
            }
        } catch (IOException e) {
            CookieClient.LOGGER.error("Failed to load values config", e);
        }
    }

    public void save(List<Module> modules) {
        Map<String, Map<String, Object>> data = new HashMap<>();
        for (Module module : modules) {
            Map<String, Object> values = new HashMap<>();
            for (Setting<?> setting : module.getSettings()) {
                values.put(setting.getName(), setting.getValue());
            }
            data.put(module.getName(), values);
        }
        try {
            Files.createDirectories(getPath().getParent());
            Files.writeString(getPath(), GSON.toJson(data));
        } catch (IOException e) {
            CookieClient.LOGGER.error("Failed to save values config", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyValue(Setting<?> setting, Object raw) {
        Object value = setting.getValue();
        if (value instanceof Number && raw instanceof Number) {
            ((Setting) setting).setValue(((Number) raw).doubleValue());
        } else if (value instanceof Boolean) {
            ((Setting) setting).setValue(Boolean.valueOf(String.valueOf(raw)));
        } else if (value instanceof String) {
            ((Setting) setting).setValue(String.valueOf(raw));
        } else if (value instanceof List) {
            List<String> list = new ArrayList<>();
            if (raw instanceof List<?> rawList) {
                for (Object o : rawList) {
                    list.add(String.valueOf(o));
                }
            }
            ((Setting) setting).setValue(list);
        }
    }
}
