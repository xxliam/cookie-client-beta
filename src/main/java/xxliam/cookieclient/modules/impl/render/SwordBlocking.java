package xxliam.cookieclient.modules.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * SwordBlocking：防砍动画（模拟旧版剑格挡姿态）。
 * <p>
 * 整体照搬 PVPUtils-1.4 的「防砍动画 / Sword Blocking Animation」（{@code Config.swordBlock}
 * + {@code ItemInHandRendererMixin.injectOldAnimation} + {@code renderOldSwordStance}）：
 * 主手持剑并按住右键时，取消原版第一人称手臂渲染，改画旧版格挡姿态。
 * <p>
 * 四种姿态的变换矩阵与数值<b>逐字照搬</b> PVPUtils（动画模式 1.7 / Push / 1.7+ / New，
 * 见 {@link #applyBlockPose}）；{@code renderOldSwordStance} 的
 * translate(-0.2, 0.126, 0.2) + rot(-102.25°, side*15°, side*80°) 亦逐值相同。
 * <p>
 * 与 PVPUtils 的三处差异（均已在下文注明原因）：
 * <ol>
 *   <li>「自动格挡」（{@code Config.autoMode} + {@code range}）在 PVPUtils 里是<b>独立的另一个模块</b>，
 *       本模块只做防砍动画，触发条件因此只保留「按住右键」；</li>
 *   <li>动画速度 / 各设置在本项目里<b>只在模块启用时生效</b>（PVPUtils 的 {@code Config.animSpeed}
 *       是无条件全局生效的，模块关掉它仍然改挥砍时长）；</li>
 *   <li>动画速度下限钳到 {@code 0.1}：PVPUtils 的滑条可拖到 0，会算出
 *       {@code 原时长 / 0 = Infinity} → {@code getCurrentSwingDuration()} 返回
 *       {@code Integer.MAX_VALUE}，挥砍动画永久卡住。</li>
 * </ol>
 */
public class SwordBlocking extends Module {

    public static SwordBlocking INSTANCE;

    /** 格挡动画模式（照搬 PVPUtils {@code Config.AnimMode} 的四个档位与默认值）。 */
    private final ModeSetting animationMode =
            new ModeSetting("Animation Mode", "1.7", "Push", "1.7+", "New").withDefault("1.7");

    private final NumberSetting offsetX = new NumberSetting("Offset X", 0.0d, -1.0d, 1.0d, 0.01d);
    private final NumberSetting offsetY = new NumberSetting("Offset Y", 0.0d, -1.0d, 1.0d, 0.01d);
    private final NumberSetting offsetZ = new NumberSetting("Offset Z", 0.0d, -1.0d, 1.0d, 0.01d);

    /** 挥砍动画速度：时长 = 原时长 / 速度（照搬 PVPUtils {@code Config.animSpeed}，默认 1.0 = 原版）。 */
    private final NumberSetting animationSpeed = new NumberSetting("Animation Speed", 1.0d, 0.0d, 4.0d, 0.05d);

    public SwordBlocking() {
        super("SwordBlocking", Category.RENDER);
        INSTANCE = this;
        addSetting(animationMode);
        addSetting(offsetX);
        addSetting(offsetY);
        addSetting(offsetZ);
        addSetting(animationSpeed);
    }

    public String getAnimationMode() {
        return animationMode.getValue();
    }

    public float getOffsetX() {
        return offsetX.getValue().floatValue();
    }

    public float getOffsetY() {
        return offsetY.getValue().floatValue();
    }

    public float getOffsetZ() {
        return offsetZ.getValue().floatValue();
    }

    public float getAnimationSpeed() {
        return animationSpeed.getValue().floatValue();
    }

    /**
     * 挥砍时长缩放系数（供 {@code LivingEntityMixin.getCurrentSwingDuration} 使用）。
     * <p>
     * {@code 1.0} 表示不改（原版时长）；{@code >1} 更快、{@code <1} 更慢。
     * 下限钳到 {@code 0.1}（10 倍慢）——PVPUtils 在 0 处会除零得到
     * {@code Integer.MAX_VALUE}，挥砍永不结束。
     */
    public float swingDurationFactor() {
        return Mth.clamp(getAnimationSpeed(), 0.1f, 4.0f);
    }

    /**
     * 是否应进入「剑格挡姿态」。
     * <p>
     * 照搬 PVPUtils {@code isBlocking} 的判定（去掉独立模块「自动格挡」的那部分）：
     * <b>本机玩家</b> + 主手为剑（{@code ItemTags.SWORDS}）+ 按住右键。
     */
    public static boolean isBlocking(AbstractClientPlayer player) {
        SwordBlocking module = INSTANCE;
        if (module == null || !module.isEnabled() || player == null) {
            return false;
        }
        if (player != Minecraft.getInstance().player) {
            return false;
        }
        return player.getMainHandItem().is(ItemTags.SWORDS)
                && Minecraft.getInstance().options.keyUse.isDown();
    }

    /**
     * 旧版剑格挡姿态（照搬 PVPUtils {@code ItemInHandRendererMixin.renderOldSwordStance}）。
     * 调用前须已叠加 {@code translate(OffsetX*side, OffsetY, OffsetZ)} 与
     * {@code applyItemArmTransform(poseStack, arm, 0.0F)}。
     */
    public void applyOldSwordStance(PoseStack poseStack, int side) {
        poseStack.translate(-0.2f, 0.126f, 0.2f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-102.25f));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 15.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 80.0f));
    }

    /**
     * 按所选动画模式应用格挡姿态矩阵（逐字照搬 PVPUtils {@code injectOldAnimation} 的
     * 四个分支；{@code swingProgress} 即原版 {@code renderArmWithItem} 的 {@code h}）。
     */
    public void applyBlockPose(PoseStack poseStack, float swingProgress, int side) {
        switch (getAnimationMode()) {
            // ---- 1.7：旧版完整挥砍 + 格挡 ----
            case "1.7" -> {
                float factor = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                float blend = 1.0f - factor;
                poseStack.translate(side * (-0.139f * blend), 0.06f * blend, 0.20f * blend);

                poseStack.translate(side * 0.430f, -0.190f, 0.520f);
                poseStack.translate(side * -0.141f, 0.08f, -0.72f);
                float f17 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
                float f22 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0f + f17 * -20.0f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * f22 * -20.0f));
                poseStack.mulPose(Axis.XP.rotationDegrees(f22 * -80.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0f));
                applyOldSwordStance(poseStack, side);
            }
            // ---- 1.7+：推进一点 + 格挡；挥砍时绕 X/Z 摆动 ----
            case "1.7+" -> {
                poseStack.translate(side * 0.15f, -0.05f, 0.0f);
                applyOldSwordStance(poseStack, side);

                float swingAmount = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                poseStack.mulPose(Axis.XP.rotationDegrees(swingAmount * -45.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * swingAmount * 20.0f));
            }
            // ---- New：1.7+ 的基座 + 只在挥砍进行中（h > 0）叠加 1.7 的位移/旋转 ----
            case "New" -> {
                poseStack.translate(side * 0.15f, -0.05f, 0.0f);
                applyOldSwordStance(poseStack, side);
                if (swingProgress > 0.0f) {
                    float swingFactor = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    poseStack.translate(side * 0.430f * swingFactor, -0.190f * swingFactor, 0.520f * swingFactor);
                    poseStack.translate(side * -0.141f * swingFactor, 0.08f * swingFactor, -0.72f * swingFactor);
                    float f17 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
                    float f22 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0f + f17 * -20.0f)));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * f22 * -20.0f));
                    poseStack.mulPose(Axis.XP.rotationDegrees(f22 * -80.0f));
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0f));
                }
            }
            // ---- Push（PVPUtils 的末位 else 分支）：短推 + 绕 Z/X 小幅摆动 ----
            default -> {
                poseStack.translate(side * 0.15f, -0.05f, 0.0f);
                applyOldSwordStance(poseStack, side);

                float swingAmount = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                poseStack.mulPose(Axis.ZP.rotationDegrees(-side * swingAmount * 35.0f));
                poseStack.mulPose(Axis.XP.rotationDegrees(swingAmount * -10.0f));
            }
        }
    }

    /** 副手手持盾牌时，格挡期间隐藏盾（照搬 PVPUtils：格挡用剑而不是盾）。 */
    public static boolean hidesShield(AbstractClientPlayer player) {
        return isBlocking(player) && player.getOffhandItem().is(Items.SHIELD);
    }

    /** 供 {@code tick()} 兜底：格挡期间把主手装备进度钉在 1.0（照搬 PVPUtils {@code updateHandPosition}）。 */
    public static boolean shouldPinEquippedProgress() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && isBlocking(mc.player);
    }
}
