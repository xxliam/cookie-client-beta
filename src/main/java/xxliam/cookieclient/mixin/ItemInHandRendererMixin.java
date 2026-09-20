package xxliam.cookieclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.modules.impl.render.SwordBlocking;
import xxliam.cookieclient.modules.impl.world.AutoTools;

/**
 * SwordBlocking（防砍动画）第一人称注入 —— 照搬 PVPUtils-1.4 {@code ItemInHandRendererMixin}。
 * <p>
 * <b>注入点 1：{@code renderArmWithItem} HEAD（cancellable）</b><br>
 * 主手持剑 + 按住右键时整体取消原版手臂渲染，自己重画旧版格挡姿态：
 * <pre>
 *   pushPose → translate(OffsetX*side, OffsetY, OffsetZ)
 *            → applyItemArmTransform(arm, 0.0F)      // 原版手臂基线（1.20.1 需显式传 0）
 *            → 按动画模式叠加矩阵（SwordBlocking.applyBlockPose）
 *            → renderItem(mainHand) → popPose
 * </pre>
 * 副手侧：若副手持盾则一并取消（照搬 PVPUtils —— 格挡用剑、不显示盾）。
 * <p>
 * 1.20.1 与 PVPUtils（1.21.11）的差异（已处理，不影响观感）：
 * <ul>
 *   <li>{@code renderItem} 在 1.20.1 多一个 {@code boolean leftHand} 参数，按
 *       {@code arm != HumanoidArm.RIGHT} 传入（等同原版调用）；</li>
 *   <li>原版 {@code renderArmWithItem} <b>自己会 pushPose</b>，所以在 HEAD 取消后必须自己
 *       push/pop（PVPUtils 也是这么做的）；</li>
 *   <li>补了 {@code player.isScoping()} 守卫：原版第一句就是「举望远镜时直接 return」，
 *       照字面取消会绕过它、举镜时把手画出来。</li>
 * </ul>
 * <p>
 * <b>注入点 2：{@code tick} TAIL</b> —— 格挡期间把 {@code mainHandHeight}/{@code oMainHandHeight}
 * 钉在 1.0（照搬 PVPUtils {@code updateHandPosition}），令装备进度保持「已举起」，不被攻击
 * 冷却的抬手动画拖走。
 * <p>
 * 另含 AutoTools 的静默手持物伪装（照搬 Naven {@code MixinItemInHandRenderer}）：
 * 重定向 {@code tick()} 里取主手物品的调用，让渲染层拿到「原槽位物品」。
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    /** 原版手臂基线变换（1.20.1 为 private，故 @Shadow）。 */
    @Shadow
    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equippedProgress) {
    }

    /** 原版物品渲染入口（1.20.1 为 public）。 */
    @Shadow
    public void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                           PoseStack poseStack, MultiBufferSource buffer, int light) {
    }

    /**
     * AutoTools 静默切工具：{@code tick()} 里第一人称手持物的来源被替换为原槽位物品，
     * 于是切到工具挖方块时自己视角仍显示切换前拿着的东西（服务器侧仍是工具）。
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack cookieClient$autoToolsMainHand(LocalPlayer player) {
        ItemStack original = player.getMainHandItem();
        return player == Minecraft.getInstance().player ? AutoTools.spoofHeldItem(original) : original;
    }

    /**
     * 防砍动画：取消原版第一人称渲染，改画旧版剑格挡姿态。
     * <p>
     * 参数表与 1.20.1 的 {@code renderArmWithItem} 逐位对应
     * （{@code swingProgress} = PVPUtils 里的 {@code h}）。
     */
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cookieClient$swordBlocking(
            AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand,
            float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (player.isScoping() || !SwordBlocking.isBlocking(player)) {
            return;
        }
        // 副手：格挡期间不显示盾（PVPUtils 同款处理）
        if (hand == InteractionHand.OFF_HAND) {
            if (SwordBlocking.hidesShield(player)) {
                ci.cancel();
            }
            return;
        }

        SwordBlocking module = SwordBlocking.INSTANCE;
        ci.cancel();

        HumanoidArm arm = player.getMainArm();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        boolean rightHand = arm == HumanoidArm.RIGHT;

        poseStack.pushPose();
        // 自定义偏移（PVPUtils：translate(offsetX * side, offsetY, offsetZ)）
        poseStack.translate(module.getOffsetX() * side, module.getOffsetY(), module.getOffsetZ());
        // 原版手臂基线：装备进度固定 0（= 完全举起），与 PVPUtils 一致
        this.applyItemArmTransform(poseStack, arm, 0.0f);
        // 各模式的格挡姿态矩阵
        module.applyBlockPose(poseStack, swingProgress, side);
        this.renderItem(player, player.getMainHandItem(),
                rightHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !rightHand, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    /** 防砍动画：格挡期间把装备进度钉在 1.0，避免攻击冷却的抬手动画干扰姿态。 */
    @Inject(method = "tick", at = @At("TAIL"))
    private void cookieClient$swordBlockPinEquippedProgress(CallbackInfo ci) {
        if (SwordBlocking.shouldPinEquippedProgress()) {
            this.mainHandHeight = 1.0f;
            this.oMainHandHeight = 1.0f;
        }
    }
}
