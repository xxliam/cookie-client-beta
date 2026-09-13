package xxliam.cookieclient.modules.impl.world;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * AutoPlay：检测到对局结束（中文服务器广播“地图评分”）后等待 Delay 秒自动执行
 * {@code /again} 进入下一局；检测到“游戏将在 1 秒 后开始”时重置状态。
 * <p>
 * 照搬 OpenZen {@code shit.zen.modules.impl.world.AutoPlay}（连带供 AutoPlayHud 消费
 * 的公开状态位）。收包由 {@code ConnectionMixin} 转发到 {@link #onPacketReceive}；
 * cookie 没有 DisconnectEvent，改为在断线/无世界时（player/level 为空）的 tick 内清状态。
 */
public class AutoPlay extends Module {

    public static AutoPlay instance;

    private final NumberSetting delay = new NumberSetting("Delay", 2.0d, 0.0d, 10.0d, 0.1d);

    /** 最近一次检测到对局结束的时间戳（-1 = 无）。 */
    public long disconnectTime = -1L;
    /** 处于“等待下局开始”状态（倒计时/断开等待）。 */
    public boolean pendingDisconnect = false;
    /** 已执行 /again 后的时间戳（-1 = 无）。 */
    public long reconnectTime = -1L;

    public AutoPlay() {
        super("AutoPlay", Category.WORLD);
        instance = this;
        addSetting(delay);
    }

    @Override
    protected void onEnable() {
        pendingDisconnect = false;
        disconnectTime = -1L;
        reconnectTime = -1L;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        pendingDisconnect = false;
        disconnectTime = -1L;
        reconnectTime = -1L;
        super.onDisable();
    }

    /** 由 {@code ConnectionMixin} 每包调用（含未启用时的快速路径）。 */
    public static void onPacketReceive(Packet<?> packet) {
        if (instance == null || !instance.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (packet instanceof ClientboundSystemChatPacket chatPacket) {
            String message = chatPacket.content().getString().replaceAll("\u00a7[0-9a-fk-or]", "").trim();
            if (message.contains("地图评分")) {
                if (instance.disconnectTime == -1L) {
                    instance.disconnectTime = System.currentTimeMillis();
                    instance.pendingDisconnect = true;
                }
            } else if (message.contains("游戏将在 1 秒 后开始")) {
                instance.disconnectTime = -1L;
                instance.pendingDisconnect = false;
            }
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // 等价 zen 的 DisconnectEvent：断线即清空状态
            if (disconnectTime != -1L || pendingDisconnect || reconnectTime != -1L) {
                disconnectTime = -1L;
                pendingDisconnect = false;
                reconnectTime = -1L;
            }
            return;
        }
        if (reconnectTime != -1L) {
            if (System.currentTimeMillis() - reconnectTime > 1000L) {
                disconnectTime = -1L;
                pendingDisconnect = false;
                reconnectTime = -1L;
            }
            return;
        }
        if (disconnectTime != -1L
                && (double) (System.currentTimeMillis() - disconnectTime) >= delay.getValue().doubleValue() * 1000.0) {
            mc.player.connection.sendCommand("again");
            reconnectTime = System.currentTimeMillis();
        }
    }

    public NumberSetting getDelay() {
        return delay;
    }
}
