package xxliam.cookieclient.utils.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import xxliam.cookieclient.CookieClient;

/**
 * 聊天消息工具：向游戏聊天框输出客户端消息。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.utils.misc.ChatUtil}。
 */
public final class ChatUtil {

    private static final String PREFIX = "\u00a7b[" + CookieClient.NAME + "]\u00a7r ";

    private ChatUtil() {
    }

    public static void message(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(Component.literal(PREFIX + message), false);
    }

    public static void message(String message, boolean actionBar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(Component.literal(PREFIX + message), actionBar);
    }
}
