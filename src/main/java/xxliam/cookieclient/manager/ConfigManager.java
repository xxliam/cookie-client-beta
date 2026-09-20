package xxliam.cookieclient.manager;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.config.KeyBindsConfig;
import xxliam.cookieclient.config.ModulesConfig;
import xxliam.cookieclient.config.ValuesConfig;

/**
 * 配置管理器：聚合 modules.json（开关）/ values.json（设置值）/ binds.json（绑键）
 * 三个配置文件的读写。
 * <p>
 * 自动保存时机：zen ClickGUI 完全关闭、快捷键切换模块后立即落盘；
 * 启动时在 {@code CookieClient.onInitialize} 末尾加载。
 */
public class ConfigManager {

    private final ModulesConfig modulesConfig = new ModulesConfig();
    private final ValuesConfig valuesConfig = new ValuesConfig();
    private final KeyBindsConfig keyBindsConfig = new KeyBindsConfig();

    /** 保存全部配置（三个文件）。 */
    public void save() {
        modulesConfig.save();
        valuesConfig.save();
        keyBindsConfig.save();
    }

    /** 加载全部配置（文件不存在则跳过对应部分）。 */
    public void load() {
        modulesConfig.load();
        valuesConfig.load();
        keyBindsConfig.load();
        CookieClient.LOGGER.info("[Config] loaded modules/values/binds from {}", ConfigManager.class.getSimpleName());
    }
}
