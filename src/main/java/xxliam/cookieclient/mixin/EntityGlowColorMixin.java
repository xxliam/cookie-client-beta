package xxliam.cookieclient.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xxliam.cookieclient.modules.impl.render.ESP;

/**
 * Glow 轮廓色定制：1.20.1 原版 glow 描边 pass 用 {@code Entity.getTeamColor()}
 * 给实体剪影上色（消费端按 {@code FastColor.ARGB32.red/green/blue} 拆包、alpha 固定 255；
 * 无队伍默认 -1 即白）。ESP Glow 档启用时，按 {@code Glow Color} 设置
 * （Theme 主题主色 / Custom 自选色 / White 白）覆盖该值，实现可自定义颜色的发光。
 * <p>
 * 仅当 {@link ESP} 处于 Glow 档且启用、且实体命中目标过滤时返回自定义色
 * （见 {@link ESP#glowColorOverride}），其余情况原值放行，不影响 Opal 与非目标实体。
 */
@Mixin(Entity.class)
public class EntityGlowColorMixin {

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void cookieClient$glowTeamColor(CallbackInfoReturnable<Integer> cir) {
        if (ESP.INSTANCE != null) {
            Integer override = ESP.INSTANCE.glowColorOverride((Entity) (Object) this);
            if (override != null) {
                cir.setReturnValue(override);
            }
        }
    }
}
