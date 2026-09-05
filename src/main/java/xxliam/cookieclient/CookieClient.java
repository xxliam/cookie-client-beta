package xxliam.cookieclient;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xxliam.cookieclient.manager.CommandManager;
import xxliam.cookieclient.manager.ConfigManager;
import xxliam.cookieclient.manager.ModuleManager;
import xxliam.cookieclient.manager.TargetManager;
import xxliam.cookieclient.notification.NotificationManager;

/**
 * Cookie Client 主入口。
 *
 * 结构参考 OpenZen 客户端（shit.zen 包），适配 Fabric 1.20.1 + Java。
 * 各 Manager 在 {@link #onInitialize()} 中初始化。
 */
public class CookieClient implements ModInitializer {

    public static final String MOD_ID = "cookie-client";
    public static final String NAME = "Cookie Client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ModuleManager MODULE_MANAGER;
    public static CommandManager COMMAND_MANAGER;
    public static ConfigManager CONFIG_MANAGER;
    public static TargetManager TARGET_MANAGER;
    public static NotificationManager NOTIFICATION_MANAGER;

    @Override
    public void onInitialize() {
        LOGGER.info("{} initializing...", NAME);

        MODULE_MANAGER = new ModuleManager();
        COMMAND_MANAGER = new CommandManager();
        CONFIG_MANAGER = new ConfigManager();
        TARGET_MANAGER = new TargetManager();
        NOTIFICATION_MANAGER = new NotificationManager();

        // 加载本地配置（不存在则跳过）
        CONFIG_MANAGER.load();

        LOGGER.info("{} initialized. Loaded {} modules.", NAME, MODULE_MANAGER.getModules().size());
    }
}
