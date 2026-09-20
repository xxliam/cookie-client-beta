package xxliam.cookieclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 按键绑定配置：保存每个模块绑定的 GLFW 键码（binds.json）。
 * <p>
 * 格式：{@code { "ModuleName": keyCode, ... }}。所有模块都写入（含未绑定的 0），
 * 这样「解绑」操作也能被持久化。
 */
public class KeyBindsConfig extends Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public KeyBindsConfig() {
        super("binds.json");
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            root.addProperty(module.getName(), module.getKeyBind());
        }
        write(GSON.toJson(root));
    }

    public void load() {
        String json = read();
        if (json == null) {
            return;
        }
        Map<String, Number> map = GSON.fromJson(json, new TypeToken<Map<String, Number>>() {
        }.getType());
        if (map == null) {
            return;
        }
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            Number key = map.get(module.getName());
            if (key != null) {
                module.setKeyBind(key.intValue());
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
