package xxliam.cookieclient.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xxliam.cookieclient.modules.impl.render.AspectRatio;
import xxliam.cookieclient.modules.impl.render.FullBright;

/**
 * 渲染相关注入：FullBright（夜视亮度，zen 语义）+ AspectRatio（投影矩阵宽高比）。
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    /** FullBright：启用时覆盖夜视缩放为设定亮度（对应 zen GameRendererPatch）。 */
    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void cookieClient$getNightVisionScale(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (FullBright.INSTANCE != null && FullBright.INSTANCE.isEnabled()) {
            cir.setReturnValue(FullBright.INSTANCE.brightnessSetting.getValue().floatValue() / 100.0f);
        }
    }

    /** AspectRatio：启用时用自定义宽高比替换投影矩阵的 aspect。 */
    @Redirect(
            method = "getProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;")
    )
    private Matrix4f cookieClient$setPerspective(Matrix4f instance, float fov, float aspect, float near, float far) {
        if (AspectRatio.INSTANCE != null && AspectRatio.INSTANCE.isEnabled()) {
            aspect = AspectRatio.INSTANCE.ratioSetting.getValue().floatValue();
        }
        return instance.setPerspective(fov, aspect, near, far);
    }
}
