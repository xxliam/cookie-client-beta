package xxliam.cookieclient.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.utils.math.MathUtil;

/**
 * 旋转计算工具（对齐 OpenZen 的 RotationUtil 方法名，供 Scaffold 等使用）。
 */
public final class RotationUtil {

    private RotationUtil() {
    }

    public static Rotation normalizeRotation(Rotation rotation) {
        return new Rotation(Mth.wrapDegrees(rotation.getYaw()), Mth.wrapDegrees(rotation.getPitch()));
    }

    /** 从 from 看向 to。 */
    public static Rotation rotationTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = toDegrees(-Math.atan2(dy, horizontalDist));
        return new Rotation(yaw, pitch);
    }

    public static float toDegrees(double radians) {
        return (float) (radians * 180.0 / Math.PI);
    }

    /** 看向方块（含预测位置与噪声）。 */
    public static Rotation rotationToBlock(BlockPos blockPos, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 predictedPos = new Vec3(
                mc.player.getX() + mc.player.getDeltaMovement().x * partialTicks,
                mc.player.getY() + mc.player.getEyeHeight() + mc.player.getDeltaMovement().y * partialTicks,
                mc.player.getZ() + mc.player.getDeltaMovement().z * partialTicks);
        double dx = blockPos.getX() - predictedPos.x + 0.5;
        double dy = blockPos.getY() - predictedPos.y + 0.5;
        double dz = blockPos.getZ() - predictedPos.z + 0.5;
        return rotationFromDeltas(addNoise(dx), addNoise(dy), addNoise(dz));
    }

    public static Rotation rotationFromDeltas(double dx, double dy, double dz) {
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDist)));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch));
    }

    public static Rotation rotationFromVec(Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player.position().add(0.0, mc.player.getEyeHeight(), 0.0);
        return rotationTo(eye, target);
    }

    private static double addNoise(double value) {
        return value + MathUtil.randomDouble(0.05, 0.08) * (MathUtil.randomDouble(0.0, 1.0) * 2.0 - 1.0);
    }

    public static float moveTowards(float maxStep, float current, float target) {
        return rotateTowards(current, target, maxStep);
    }

    public static float rotateTowards(float current, float target, float maxStep) {
        float diff = Mth.wrapDegrees(target - current);
        if (diff > maxStep) {
            diff = maxStep;
        }
        if (diff < -maxStep) {
            diff = -maxStep;
        }
        return current + diff;
    }

    public static float clampAngle(float angle, float max) {
        if (Math.abs(angle) < max) {
            return angle;
        }
        if (angle > 0.0f) {
            return max;
        }
        return -max;
    }

    public static double angleDiffDouble(float angleA, float angleB) {
        return Mth.wrapDegrees(angleA - angleB);
    }

    /** 平滑逼近目标旋转（按 yaw/pitch 距离比例分配步长）。 */
    public static Rotation smoothRotation(Rotation current, Rotation target, double speed) {
        float targetYaw = target.getYaw();
        float targetPitch = target.getPitch();
        float currYaw = current.getYaw();
        float currPitch = current.getPitch();
        if (speed != 0.0) {
            double yawDiff = Mth.wrapDegrees(target.getYaw() - current.getYaw());
            double pitchDiff = targetPitch - currPitch;
            double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
            if (dist > 1.0E-6) {
                double yawRatio = Math.abs(yawDiff / dist);
                double pitchRatio = Math.abs(pitchDiff / dist);
                double maxYawStep = speed * yawRatio;
                double maxPitchStep = speed * pitchRatio;
                float yawStep = (float) Math.max(Math.min(yawDiff, maxYawStep), -maxYawStep);
                float pitchStep = (float) Math.max(Math.min(pitchDiff, maxPitchStep), -maxPitchStep);
                targetYaw = currYaw + yawStep;
                targetPitch = currPitch + pitchStep;
            }
        }
        return new Rotation(targetYaw, Mth.clamp(targetPitch, -90.0f, 90.0f));
    }
}
