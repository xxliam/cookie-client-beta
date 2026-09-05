package xxliam.cookieclient.command;

/**
 * 客户端指令基类。通过聊天框 {@code .<命令名> <参数>} 触发。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.command.Command}。
 */
public abstract class Command {

    private final String name;
    private final String description;
    private final String[] aliases;

    protected Command(String name, String description, String... aliases) {
        this.name = name;
        this.description = description;
        this.aliases = aliases;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getAliases() {
        return aliases;
    }

    public abstract void execute(String[] args);
}
