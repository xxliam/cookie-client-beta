package xxliam.cookieclient.manager;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.config.KeyBindsConfig;
import xxliam.cookieclient.config.ModulesConfig;
import xxliam.cookieclient.config.ValuesConfig;

/**
 * 配置管理器：负责模块状态与设置项的加载 / 保存。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.manager.ConfigManager}。
 */
public class ConfigManager {

    private final ModulesConfig modulesConfig = new ModulesConfig();
    private final ValuesConfig valuesConfig = new ValuesConfig();
    private final KeyBindsConfig keyBindsConfig = new KeyBindsConfig();

    /**
     * 启动时加载配置。配置文件不存在时静默跳过。
     */
    public void load() {
        modulesConfig.load(CookieClient.MODULE_MANAGER.getModules());
        valuesConfig.load(CookieClient.MODULE_MANAGER.getModules());
        keyBindsConfig.load(CookieClient.MODULE_MANAGER.getModules());
        CookieClient.LOGGER.info("Config loaded.");
    }

    public void save() {
        modulesConfig.save(CookieClient.MODULE_MANAGER.getModules());
        valuesConfig.save(CookieClient.MODULE_MANAGER.getModules());
        keyBindsConfig.save(CookieClient.MODULE_MANAGER.getModules());
        CookieClient.LOGGER.info("Config saved.");
    }
}
