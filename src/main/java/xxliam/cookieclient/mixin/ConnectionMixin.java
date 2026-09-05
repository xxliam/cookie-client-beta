package xxliam.cookieclient.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.modules.impl.movement.Scaffold;

/**
 * 收包钩子：供 Scaffold 检测速度包（离合器用）。
 */
@Mixin(Connection.class)
public class ConnectionMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void cookieClient$channelRead0(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        Scaffold.onPacketReceive(packet);
    }
}
