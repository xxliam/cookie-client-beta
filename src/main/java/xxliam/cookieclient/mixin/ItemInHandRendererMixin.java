package xxliam.cookieclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.modules.impl.render.Animations;

/**
 * Animations 第一人称注入（替代原 OldHitting 注入）。
 * <p>
 * 注入点：{@code renderArmWithItem} 内唯一的最终 {@code renderItem(...)} 调用前
 * （1.20.1 中所有一般物品[含主手剑]在该点汇合渲染）。此时 vanilla 的手臂/手持基线
 * 已全部应用，在此之上：
 * <ol>
 *   <li>先应用主手偏移 translate(OffsetX, OffsetY, Scale)（对应 opal HeldItemRendererMixin
 *       {@code hookRenderFirstPersonItem}，数值照搬）；</li>
 *   <li>若为「剑格挡姿态」再套用 opal {@code applyTransformations} 的格挡动画模式矩阵。</li>
 * </ol>
 * 格挡姿态判定（cookie 1.20.1 语义，opal 的 FakeAB/NoSlow 判定在 cookie 无对应系统）：
 * 渲染本机玩家主手持剑、未在使用物品、按住右键且副手为空。
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void cookieClient$animations(
            AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand,
            float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        Animations animations = Animations.INSTANCE;
        if (animations == null || !animations.isEnabled()) {
            return;
        }

        // 主手偏移：作用于第一人称物品（opal 主手 translate x/y/z）
        if (hand == InteractionHand.MAIN_HAND) {
            animations.applyMainHandOffset(poseStack);
            if (isSwordBlockingPose(player, stack)) {
                animations.applyTransformations(poseStack, swingProgress);
            }
        }
    }

    private boolean isSwordBlockingPose(AbstractClientPlayer player, ItemStack stack) {
        Animations animations = Animations.INSTANCE;
        if (animations == null || !animations.isSwordBlocking()) {
            return false;
        }
        if (!(stack.getItem() instanceof SwordItem)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player || player.isUsingItem()) {
            return false;
        }
        return mc.options.keyUse.isDown() && player.getOffhandItem().isEmpty();
    }
}
