package xxliam.cookieclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xxliam.cookieclient.modules.impl.render.ESP;

/**
 * 抑制原版玩家名牌（照搬 OpenOpal {@code LivingEntityRendererMixin.hookHasLabel}）。
 * <p>
 * Opal 在启用自定义 ESP 名牌后会让 {@code hasLabel=false} 阻止原版名牌渲染，避免双重名牌。
 * cookie 对应 1.20.1 Mojmap 的判定点为 {@code LivingEntityRenderer#shouldShowName}。
 */
@Mixin(net.minecraft.client.renderer.entity.LivingEntityRenderer.class)
public abstract class LivingEntityRendererLabelMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void cookieClient$suppressVanillaNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        ESP esp = ESP.INSTANCE;
        if (esp != null && esp.isOpalModeActive() && esp.areOpalNameTagsEnabled() && esp.opalMatchesTarget(entity)) {
            cir.setReturnValue(false);
        }
    }
}
