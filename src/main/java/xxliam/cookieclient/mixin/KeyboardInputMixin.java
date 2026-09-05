package xxliam.cookieclient.mixin;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.modules.impl.movement.GuiMove;

/**
 * GuiMove：键盘输入计算完成后，由 GuiMove 覆盖移动输入。
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void cookieClient$tick(boolean isSneaking, float sneakMultiplier, CallbackInfo ci) {
        GuiMove.onStrafe((Input) (Object) this);
    }
}
