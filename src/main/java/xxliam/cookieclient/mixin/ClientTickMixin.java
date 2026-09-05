package xxliam.cookieclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.CookieClient;

/**
 * 每游戏刻驱动已启用模块的 onTick。
 */
@Mixin(Minecraft.class)
public class ClientTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void cookieclient$onClientTick(CallbackInfo ci) {
        if (CookieClient.MODULE_MANAGER != null) {
            CookieClient.MODULE_MANAGER.onTick();
        }
    }
}
