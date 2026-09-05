package xxliam.cookieclient.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.utils.rotation.RotationHandler;

/**
 * 发包旋转：在 {@code sendPosition} 前后应用 / 恢复目标旋转。
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void cookieClient$sendPosition(CallbackInfo ci) {
        RotationHandler.applyToPlayer((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void cookieClient$sendPositionPost(CallbackInfo ci) {
        RotationHandler.restore((LocalPlayer) (Object) this);
    }
}
