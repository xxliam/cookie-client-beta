package xxliam.cookieclient.manager;

import xxliam.cookieclient.command.Command;
import xxliam.cookieclient.command.impl.BindCommand;
import xxliam.cookieclient.command.impl.ConfigCommand;
import xxliam.cookieclient.command.impl.ToggleCommand;
import xxliam.cookieclient.utils.misc.ChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 命令管理器：注册命令、解析聊天框指令。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.manager.CommandManager}。
 */
public class CommandManager {

    public static final String PREFIX = ".";

    private final List<Command> commands = new ArrayList<>();

    public CommandManager() {
        add(new ToggleCommand());
        add(new BindCommand());
        add(new ConfigCommand());
    }

    public void add(Command command) {
        commands.add(command);
    }

    public List<Command> getCommands() {
        return commands;
    }

    public Command getCommand(String name) {
        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(name)) {
                return command;
            }
            for (String alias : command.getAliases()) {
                if (alias.equalsIgnoreCase(name)) {
                    return command;
                }
            }
        }
        return null;
    }

    /**
     * 处理聊天框输入。形如 {@code .toggle sprint}。
     */
    public void execute(String message) {
        if (message == null || !message.startsWith(PREFIX)) {
            return;
        }
        String[] parts = message.substring(PREFIX.length()).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        Command command = getCommand(parts[0].toLowerCase(Locale.ROOT));
        if (command == null) {
            ChatUtil.message("Unknown command: " + parts[0]);
            return;
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        command.execute(args);
    }
}
