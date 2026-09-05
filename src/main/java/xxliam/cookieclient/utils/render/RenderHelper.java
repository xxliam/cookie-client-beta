package xxliam.cookieclient.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * 渲染辅助：PoseStack 缩放/旋转、shader 颜色等。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.utils.render.RenderHelper}（仅搬运 GUI 所需部分）。
 */
public final class RenderHelper {

    private RenderHelper() {
    }

    /** 以 (pivotX, pivotY) 为轴心缩放。 */
    public static void pushScaleAround(PoseStack poseStack, float pivotX, float pivotY, float scale) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0f);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-pivotX, -pivotY, 0.0f);
    }

    /** 以 (pivotX, pivotY) 为轴心旋转。 */
    public static void pushRotateAround(PoseStack poseStack, float pivotX, float pivotY, float angleDegrees) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angleDegrees));
        poseStack.translate(-pivotX, -pivotY, 0.0f);
    }

    public static void popPose(PoseStack poseStack) {
        poseStack.popPose();
    }

    public static void resetShaderColor() {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void setShaderColor(int color) {
        RenderSystem.setShaderColor(
                (float) ColorUtil.getRed(color) / 255.0f,
                (float) ColorUtil.getGreen(color) / 255.0f,
                (float) ColorUtil.getBlue(color) / 255.0f,
                (float) ColorUtil.getAlpha(color) / 255.0f);
    }
}
