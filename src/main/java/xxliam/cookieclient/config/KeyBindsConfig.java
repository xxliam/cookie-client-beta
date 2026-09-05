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
 * 按键绑定配置：保存每个模块绑定的 GLFW 键码（binds.json）。
 * <p>
 * 格式：{ "ModuleName": keyCode, ... }，未绑定（keyCode <= 0）不写入。
 */
public class KeyBindsConfig extends Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public KeyBindsConfig() {
        super("binds.json");
    }

    public void load(List<Module> modules) {
        if (!Files.exists(getPath())) {
            return;
        }
        try {
            Map<String, Number> binds = GSON.fromJson(
                    Files.readString(getPath()),
                    new TypeToken<Map<String, Number>>() {
                    }.getType());
            if (binds == null) {
                return;
            }
            for (Module module : modules) {
                Number key = binds.get(module.getName());
                if (key != null) {
                    module.setKeyBind(key.intValue());
                }
            }
        } catch (IOException e) {
            CookieClient.LOGGER.error("Failed to load key binds config", e);
        }
    }

    public void save(List<Module> modules) {
        Map<String, Integer> binds = new HashMap<>();
        for (Module module : modules) {
            if (module.getKeyBind() > 0) {
                binds.put(module.getName(), module.getKeyBind());
            }
        }
        try {
            Files.createDirectories(getPath().getParent());
            Files.writeString(getPath(), GSON.toJson(binds));
        } catch (IOException e) {
            CookieClient.LOGGER.error("Failed to save key binds config", e);
        }
    }
}
