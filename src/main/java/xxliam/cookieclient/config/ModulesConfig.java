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
 * 模块状态配置：保存每个模块的启用 / 禁用状态（modules.json）。
 * <p>
 * 只写 {@link Module#shouldPersistEnabled()} 为 true 的模块——GUI 类模块
 * （如 ClickGui）的 enabled 只在界面打开期间有意义，绝不持久化。
 */
public class ModulesConfig extends Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ModulesConfig() {
        super("modules.json");
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            if (module.shouldPersistEnabled()) {
                root.addProperty(module.getName(), module.isEnabled());
            }
        }
        write(GSON.toJson(root));
    }

    public void load() {
        String json = read();
        if (json == null) {
            return;
        }
        Map<String, Boolean> map = GSON.fromJson(json, new TypeToken<Map<String, Boolean>>() {
        }.getType());
        if (map == null) {
            return;
        }
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            Boolean enabled = map.get(module.getName());
            if (enabled != null && module.shouldPersistEnabled()) {
                module.setEnabled(enabled);
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
