package xxliam.cookieclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xxliam.cookieclient.modules.impl.render.NameProtect;

/**
 * NameProtect：在聊天消息显示前替换其中的玩家名。
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), argsOnly = true, index = 1
    )
    private Component cookieClient$modifyMessage1(Component message) {
        return Component.literal(NameProtect.replacePlayerName(message.getString()));
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, index = 1
    )
    private Component cookieClient$modifyMessage2(Component message) {
        return Component.literal(NameProtect.replacePlayerName(message.getString()));
    }
}
