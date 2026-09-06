package xxliam.cookieclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link GameRenderer} 私有渲染方法访问器（照搬 OpenOpal {@code GameRendererAccessor}）。
 * <p>
 * Opal ESP 的 2D 投影（{@code ESPUtility.createMatrixStack}）需要重建世界渲染时的
 * 完整投影矩阵：动态 FOV + 受击倾斜（bobHurt）+ 视角晃动（bobView）+ 相机朝向。
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Invoker("getFov")
    double cookieClient$getFov(Camera camera, float partialTick, boolean changingFov);

    @Invoker("bobHurt")
    void cookieClient$bobHurt(PoseStack poseStack, float partialTick);

    @Invoker("bobView")
    void cookieClient$bobView(PoseStack poseStack, float partialTick);
}
