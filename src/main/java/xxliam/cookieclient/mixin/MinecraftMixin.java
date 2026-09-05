package xxliam.cookieclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xxliam.cookieclient.modules.impl.render.ESP;

/**
 * ESP Glow 模式：在 {@code shouldEntityAppearGlowing} 中让目标实体发光。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Redirect(
            method = "shouldEntityAppearGlowing",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isCurrentlyGlowing()Z")
    )
    private boolean cookieClient$shouldEntityAppearGlowing(Entity entity) {
        if (ESP.INSTANCE != null && ESP.INSTANCE.isGlowing(entity)) {
            return true;
        }
        return entity.isCurrentlyGlowing();
    }
}
