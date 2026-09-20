package xxliam.cookieclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.Setting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 设置值配置：保存每个模块所有 {@link Setting} 的取值（values.json）。
 * <p>
 * 格式：{@code { "ModuleName": { "SettingName": value, ... }, ... }}。
 * 各 Setting 子类自带 {@code save/load} 序列化（含颜色 / 多选列表），
 * 本类只负责按「模块 → 设置」两级路由。
 * <p>
 * 注意：隐藏设置（如 HUD 拖动偏移的 {@code NumberSetting(..., () -> false)}）
 * 同样落盘——可见性只是 UI 概念，偏移量必须持久化。
 */
public class ValuesConfig extends Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ValuesConfig() {
        super("values.json");
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            if (module.getSettings().isEmpty()) {
                continue;
            }
            JsonObject moduleJson = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                setting.save(moduleJson);
            }
            root.add(module.getName(), moduleJson);
        }
        write(GSON.toJson(root));
    }

    public void load() {
        String json = read();
        if (json == null) {
            return;
        }
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null) {
            return;
        }
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            JsonObject moduleJson = root.getAsJsonObject(module.getName());
            if (moduleJson == null) {
                continue;
            }
            for (Setting<?> setting : module.getSettings()) {
                JsonElement element = moduleJson.get(setting.getName());
                if (element == null || element.isJsonNull()) {
                    continue;
                }
                try {
                    setting.load(element);
                } catch (Exception e) {
                    // 单个设置项的坏值不应拖垮整个配置加载（如改版后类型不匹配）
                    CookieClient.LOGGER.warn("[Config] failed to load setting {}.{}",
                            module.getName(), setting.getName(), e);
                }
            }
        }
    }

    private void write(String json) {
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(getPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CookieClient.LOGGER.warn("[Config] failed to write {}", getFileName(), e);
        }
    }

    private String read() {
        Path path = getPath();
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CookieClient.LOGGER.warn("[Config] failed to read {}", getFileName(), e);
            return null;
        }
    }
}
