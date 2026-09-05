package xxliam.cookieclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xxliam.cookieclient.modules.impl.movement.NoDelay;
import xxliam.cookieclient.modules.impl.render.FullBright;

/**
 * FullBright：玩家伪造夜视效果（照搬 zen LivingEntityPatch.hasEffect）。
 * 启用时对本地玩家返回 hasEffect(NIGHT_VISION)=true，令方块/实体渲染与 UI
 * 按夜视判定走全亮路径。NoDelay：每刻清零玩家的跳跃延迟。
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Shadow
    private int noJumpDelay;

    /** FullBright：玩家伪造拥有夜视。 */
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void cookieClient$hasEffect(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        if (FullBright.INSTANCE != null && FullBright.INSTANCE.isEnabled()
                && effect == MobEffects.NIGHT_VISION
                && (Object) this == Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void cookieClient$aiStep(CallbackInfo ci) {
        if (NoDelay.INSTANCE != null && NoDelay.INSTANCE.isEnabled() && NoDelay.INSTANCE.fastDig.getValue()) {
            noJumpDelay = 0;
        }
    }
}
