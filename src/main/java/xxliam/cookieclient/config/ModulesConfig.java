package xxliam.cookieclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Module;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块状态配置：保存每个模块的启用 / 禁用状态（modules.json）。
 */
public class ModulesConfig extends Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ModulesConfig() {
        super("modules.json");
    }

    public void load(List<Module> modules) {
        if (!Files.exists(getPath())) {
            return;
        }
        try {
            Map<String, Boolean> states = GSON.fromJson(
                    Files.readString(getPath()),
                    new TypeToken<Map<String, Boolean>>() {
                    }.getType());
            if (states == null) {
                return;
            }
            for (Module module : modules) {
                Boolean enabled = states.get(module.getName());
                if (enabled != null && enabled) {
                    module.enable();
                }
            }
        } catch (IOException e) {
            CookieClient.LOGGER.error("Failed to load modules config", e);
        }
    }

    public void save(List<Module> modules) {
        Map<String, Boolean> states = new HashMap<>();
        for (Module module : modules) {
            states.put(module.getName(), module.isEnabled());
        }
        try {
            Files.createDirectories(getPath().getParent());
            Files.writeString(getPath(), GSON.toJson(states));
        } catch (IOException e) {
            CookieClient.LOGGER.error("Failed to save modules config", e);
        }
    }
}
