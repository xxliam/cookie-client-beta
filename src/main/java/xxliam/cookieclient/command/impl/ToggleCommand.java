package xxliam.cookieclient.command.impl;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.command.Command;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.utils.misc.ChatUtil;

/**
 * 开关模块：{@code .toggle <模块名>}，别名 {@code .t}。
 */
public class ToggleCommand extends Command {

    public ToggleCommand() {
        super("toggle", "Toggle a module", "t");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            ChatUtil.message("Usage: .toggle <module>");
            return;
        }
        Module module = CookieClient.MODULE_MANAGER.getModule(args[0]);
        if (module == null) {
            ChatUtil.message("Module not found: " + args[0]);
            return;
        }
        module.toggle();
        ChatUtil.message(module.getName() + (module.isEnabled() ? " \u00a7aenabled" : " \u00a7cdisabled"));
    }
}
