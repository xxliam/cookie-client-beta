package xxliam.cookieclient.command.impl;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.command.Command;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.utils.misc.ChatUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * 绑定按键：{@code .bind <模块名> <键名>}。
 * 示例：{@code .bind sprint V}
 */
public class BindCommand extends Command {

    public BindCommand() {
        super("bind", "Bind a module to a key", "b");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            ChatUtil.message("Usage: .bind <module> <key>");
            return;
        }
        Module module = CookieClient.MODULE_MANAGER.getModule(args[0]);
        if (module == null) {
            ChatUtil.message("Module not found: " + args[0]);
            return;
        }
        int key = parseKey(args[1]);
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            ChatUtil.message("Unknown key: " + args[1]);
            return;
        }
        module.setKeyBind(key);
        ChatUtil.message("Bound " + module.getName() + " to " + args[1].toUpperCase());
    }

    private int parseKey(String name) {
        if (name.length() == 1) {
            char c = Character.toUpperCase(name.charAt(0));
            if (c >= 'A' && c <= 'Z') {
                return GLFW.GLFW_KEY_A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_0 + (c - '0');
            }
        }
        // 鼠标侧键：mouse4~mouse8（兼容 m4/mb4 简写），GLFW 码 = 物理键号 - 1
        String lower = name.toLowerCase(Locale.ROOT);
        for (int mouse = 4; mouse <= 8; mouse++) {
            if (lower.equals("mouse" + mouse) || lower.equals("m" + mouse) || lower.equals("mb" + mouse)) {
                return mouse - 1;
            }
        }
        return GLFW.GLFW_KEY_UNKNOWN;
    }
}
