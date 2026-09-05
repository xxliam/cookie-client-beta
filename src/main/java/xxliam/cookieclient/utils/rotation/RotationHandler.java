package xxliam.cookieclient.utils.rotation;

import net.minecraft.client.player.LocalPlayer;

/**
 * 旋转处理器：集中管理目标旋转，并在发包时应用（简化版，对齐 OpenZen 的 RotationHandler 核心）。
 * <p>
 * Scaffold / KillAura 等模块通过 {@link #setTargetRotation} 设置目标旋转；
 * {@code LocalPlayerMixin} 在 {@code sendPosition} 前后调用 {@link #applyToPlayer} / {@link #restore}
 * 使发包使用目标旋转、视觉保持原视角。
 */
public final class RotationHandler {

    public static Rotation targetRotation;
    public static Rotation prevRotation;
    public static Rotation sentRotation;
    public static Rotation prevSentRotation;
    public static boolean isRotating;

    private static float prevYaw;
    private static float prevPitch;

    private RotationHandler() {
    }

    public static void setTargetRotation(Rotation rotation) {
        targetRotation = rotation;
    }

    /** 发包前：保存原视角并应用目标旋转。 */
    public static void applyToPlayer(LocalPlayer player) {
        if (!isRotating || targetRotation == null || player == null) {
            return;
        }
        float yaw = targetRotation.getYaw();
        float pitch = targetRotation.getPitch();
        if (Float.isNaN(yaw) || Float.isNaN(pitch)) {
            return;
        }
        prevYaw = player.getYRot();
        prevPitch = player.getXRot();
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    /** 发包后：恢复原视角。 */
    public static void restore(LocalPlayer player) {
        if (!isRotating || player == null) {
            return;
        }
        player.setYRot(prevYaw);
        player.setXRot(prevPitch);
    }
}
