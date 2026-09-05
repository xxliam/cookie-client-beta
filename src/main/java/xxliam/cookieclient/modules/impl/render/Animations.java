package xxliam.cookieclient.modules.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * Animations：修改游戏内（第一人称）动画。
 * <p>
 * 移植自 opal {@code AnimationsModule}（第一人称核心包）：
 * 剑格挡动画（Block animation 模式：1.7/1.8/Rub/Stella/Bounce/Diagonal/Swank）与
 * 主手物品偏移（Scale / Offset X / Offset Y）。
 * <p>
 * 实际渲染由 {@code ItemInHandRendererMixin} 在 {@code renderArmWithItem} 的最终
 * {@code renderItem} 前注入 {@link #applyTransformations} 实现（1.20.1 语义：
 * 持剑并按住右键[副手空、未使用物品]时按所选模式套用格挡/挥动变换；
 * opal 的 FakeAB / NoSlow / KillAura 联动在 cookie 无对应系统，已裁剪并注明）。
 * <p>
 * 变换数值照搬 opal：{@code applyBlockTransformation} = translate(-0.15,0.16,0.15)
 * + rotY(-18)/rotZ(82)/rotY(112)；{@code applySwingTransformation} 与各模式旋转角与 opal
 * 完全一致（1.21 的 RotationAxis 对应 1.20.1 的 {@link Axis}）。
 */
public class Animations extends Module {

    public static Animations INSTANCE;

    // Sword blocking（opal AnimationsModule 同名组）
    private final BooleanSetting swordBlocking = new BooleanSetting("Enabled", true);
    private final ModeSetting blockAnimationMode = new ModeSetting("Block animation", "1.7", "1.8", "Rub", "Stella", "Bounce", "Diagonal", "Swank").withDefault("1.7");

    // Item（opal：mainHandScale/X/Y → translate(OffsetX, OffsetY, Scale)）
    private final NumberSetting mainHandScale = new NumberSetting("Scale", 0.0, -2.0, 2.0, 0.1);
    private final NumberSetting mainHandX = new NumberSetting("Offset X", 0.0, -2.0, 2.0, 0.1);
    private final NumberSetting mainHandY = new NumberSetting("Offset Y", 0.0, -2.0, 2.0, 0.1);

    public Animations() {
        super("Animations", Category.RENDER);
        INSTANCE = this;
        addSetting(swordBlocking);
        addSetting(blockAnimationMode);
        addSetting(mainHandX);
        addSetting(mainHandY);
        addSetting(mainHandScale);
    }

    // ==================== opal 对应 getter ====================

    public boolean isSwordBlocking() {
        return swordBlocking.getValue();
    }

    public String getBlockAnimationMode() {
        return blockAnimationMode.getValue();
    }

    public float getMainHandScale() {
        return mainHandScale.getValue().floatValue();
    }

    public float getMainHandX() {
        return mainHandX.getValue().floatValue();
    }

    public float getMainHandY() {
        return mainHandY.getValue().floatValue();
    }

    // ==================== 变换（照搬 opal applyTransformations / BlockUtility） ====================

    /**
     * 主手偏移（opal HeldItemRendererMixin：matrices.translate(OffsetX, OffsetY, Scale)）。
     * 返回是否发生了位移。
     */
    public boolean applyMainHandOffset(PoseStack poseStack) {
        float x = getMainHandX();
        float y = getMainHandY();
        float z = getMainHandScale();
        if (x == 0.0f && y == 0.0f && z == 0.0f) {
            return false;
        }
        poseStack.translate(x, y, z);
        return true;
    }

    /** opal AnimationsModule.applyTransformations：按所选格挡动画模式应用矩阵。 */
    public void applyTransformations(PoseStack poseStack, float swingProgress) {
        float convertedProgress = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);

        switch (getBlockAnimationMode()) {
            case "1.7" -> {
                applySwingTransformation(poseStack, swingProgress, convertedProgress);
                applyBlockTransformation(poseStack);
            }
            case "1.8" -> applyBlockTransformation(poseStack);
            case "Rub" -> {
                applyBlockTransformation(poseStack);
                poseStack.mulPose(Axis.YP.rotationDegrees(f * -30.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(convertedProgress * -30.0f));
            }
            case "Stella" -> {
                applySwingTransformation(poseStack, swingProgress, convertedProgress);
                poseStack.translate(-0.15f, 0.16f, 0.15f);
                poseStack.mulPose(Axis.YP.rotationDegrees(-24.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(75.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
            case "Bounce" -> {
                applyBlockTransformation(poseStack);
                poseStack.mulPose(Axis.XP.rotationDegrees(0.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(convertedProgress * 42.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-convertedProgress * 22.0f));
            }
            case "Diagonal" -> {
                applyBlockTransformation(poseStack);
                poseStack.mulPose(Axis.XP.rotationDegrees(5.0f - (convertedProgress * 32.0f)));
                poseStack.mulPose(Axis.YP.rotationDegrees(0.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(0.0f));
            }
            case "Swank" -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(45.0f + f * -5.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(convertedProgress * -20.0f));
                poseStack.mulPose(Axis.XP.rotationDegrees(convertedProgress * -40.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(-45.0f));
                applyBlockTransformation(poseStack);
            }
        }
    }

    /** opal BlockUtility.applySwingTransformation（数值照搬）。 */
    public static void applySwingTransformation(PoseStack poseStack, float swingProgress, float convertedProgress) {
        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0f + f * -20.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(convertedProgress * -20.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(convertedProgress * -80.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(-45.0f));
    }

    /** opal BlockUtility.applyBlockTransformation（数值照搬）。 */
    public static void applyBlockTransformation(PoseStack poseStack) {
        poseStack.translate(-0.15f, 0.16f, 0.15f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-18.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(82.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(112.0f));
    }
}
