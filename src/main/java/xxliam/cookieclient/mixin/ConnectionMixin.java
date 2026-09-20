package xxliam.cookieclient.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.modules.impl.misc.InvManager;
import xxliam.cookieclient.modules.impl.render.ChestESP;
import xxliam.cookieclient.modules.impl.world.AutoPlay;

/**
 * 收/发包钩子：收包供 AutoPlay 检测对局结束广播、ChestESP 检测箱子开合（方块事件包）；
 * 发包供 InvManager 在背包界面移动/攻击时自动补发容器关闭包。
 * <p>
 * 原 Scaffold 的速度包离合器（onPacketReceive）已随 zen 版 Scaffold 删除
 * （LB Normal 移植版无离合器逻辑）。
 */
@Mixin(Connection.class)
public class ConnectionMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void cookieClient$channelRead0(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        AutoPlay.onPacketReceive(packet);
        ChestESP.onPacketReceive(packet);
    }

    @Inject(method = "send", at = @At("HEAD"))
    private void cookieClient$send(Packet<?> packet, CallbackInfo ci) {
        InvManager.onPacketSend(packet);
    }
}
