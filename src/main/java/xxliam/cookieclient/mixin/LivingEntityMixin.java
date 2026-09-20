package xxliam.cookieclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xxliam.cookieclient.modules.impl.movement.NoDelay;
import xxliam.cookieclient.modules.impl.render.AntiNausea;
import xxliam.cookieclient.modules.impl.render.FullBright;
import xxliam.cookieclient.modules.impl.render.SwordBlocking;

/**
 * FullBright：玩家伪造夜视效果（照搬 zen LivingEntityPatch.hasEffect）。
 * 启用时对本地玩家返回 hasEffect(NIGHT_VISION)=true，令方块/实体渲染与 UI
 * 按夜视判定走全亮路径。AntiNausea：对本地玩家返回 hasEffect(CONFUSION)=false，
 * 屏蔽 1.20.1 恶心视觉（入口见 {@link AntiNausea}）。NoDelay：每刻清零玩家的跳跃延迟。
 * SwordBlocking：挥砍动画速度（照搬 PVPUtils {@code MixinLivingEntity}）。
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Shadow
    private int noJumpDelay;

    /** 挥砍进度（照搬 PVPUtils {@code preventSwingReset} 用到的字段）。 */
    @Shadow
    public int swingTime;

    /** FullBright：玩家伪造拥有夜视。AntiNausea：玩家伪造没有反胃（照搬 Naven MixinLivingEntity.hasEffect）。 */
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void cookieClient$hasEffect(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (FullBright.INSTANCE != null && FullBright.INSTANCE.isEnabled()
                && effect == MobEffects.NIGHT_VISION) {
            cir.setReturnValue(true);
        }
        if (AntiNausea.INSTANCE != null && AntiNausea.INSTANCE.isEnabled()
                && effect == MobEffects.CONFUSION) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void cookieClient$aiStep(CallbackInfo ci) {
        if (NoDelay.INSTANCE != null && NoDelay.INSTANCE.isEnabled() && NoDelay.INSTANCE.fastDig.getValue()) {
            noJumpDelay = 0;
        }
    }

    /**
     * SwordBlocking：挥砍动画速度（照搬 PVPUtils {@code modifySwingDuration}）。
     * <p>
     * 挥砍时长 = 原时长 / 速度，仅对玩家生效、且只在模块启用时改（PVPUtils 是无条件全局生效）。
     * 下限经 {@link SwordBlocking#swingDurationFactor()} 钳到 0.1 —— 速度 0 会除零得到
     * {@code Integer.MAX_VALUE}，令挥砍动画永久卡住。
     */
    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void cookieClient$swingDuration(CallbackInfoReturnable<Integer> cir) {
        SwordBlocking module = SwordBlocking.INSTANCE;
        if (module == null || !module.isEnabled() || !((Object) this instanceof Player)) {
            return;
        }
        float factor = module.swingDurationFactor();
        if (factor == 1.0f) {
            return;
        }
        cir.setReturnValue(Math.max(1, (int) (cir.getReturnValueI() / factor)));
    }

    /**
     * SwordBlocking：动画变慢（速度 &lt; 1）时，挥砍进行中不再被新的攻击打断
     * （照搬 PVPUtils {@code preventSwingReset}）。
     */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void cookieClient$preventSwingReset(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        SwordBlocking module = SwordBlocking.INSTANCE;
        if (module != null && module.isEnabled() && (Object) this instanceof Player
                && this.swingTime > 0 && module.getAnimationSpeed() < 1.0f) {
            ci.cancel();
        }
    }
}
