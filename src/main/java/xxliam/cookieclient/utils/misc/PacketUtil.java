package xxliam.cookieclient.utils.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;

import java.util.ArrayList;

/**
 * 发包工具（对齐 OpenZen 的 PacketUtil，精简）。
 */
public final class PacketUtil {

    public static final ArrayList<Packet<ServerGamePacketListener>> queuedPackets = new ArrayList<>();

    private PacketUtil() {
    }

    /** 队列发包：包是否在队列中（用于绕过重复拦截）。 */
    public static boolean shouldBypass(Packet<ServerGamePacketListener> packet) {
        if (queuedPackets.contains(packet)) {
            queuedPackets.remove(packet);
            return true;
        }
        return false;
    }

    public static void sendQueued(Packet<ServerGamePacketListener> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        queuedPackets.add(packet);
        mc.player.connection.send(packet);
    }

    public static void send(Packet<ServerGamePacketListener> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.connection.send(packet);
    }
}
