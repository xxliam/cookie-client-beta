package xxliam.cookieclient.command.impl;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.command.Command;
import xxliam.cookieclient.utils.misc.ChatUtil;

/**
 * 配置命令：{@code .config save|load}。
 */
public class ConfigCommand extends Command {

    public ConfigCommand() {
        super("config", "Save or load config", "cfg");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            ChatUtil.message("Usage: .config <save|load>");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "save" -> {
                CookieClient.CONFIG_MANAGER.save();
                ChatUtil.message("Config saved.");
            }
            case "load" -> {
                CookieClient.CONFIG_MANAGER.load();
                ChatUtil.message("Config loaded.");
            }
            default -> ChatUtil.message("Usage: .config <save|load>");
        }
    }
}
