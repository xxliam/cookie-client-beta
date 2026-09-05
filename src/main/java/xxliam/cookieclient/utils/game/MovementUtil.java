package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 移动工具：移动状态判断与速度控制。
 */
public final class MovementUtil {

    private MovementUtil() {
    }

    public static LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    public static boolean isMoving() {
        LocalPlayer player = getPlayer();
        return player != null && (player.zza != 0 || player.xxa != 0);
    }

    /** 是否有移动输入（基于 Input 的 impulse，对齐 OpenZen）。 */
    public static boolean isInputActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null
                && (mc.player.input.forwardImpulse != 0.0f || mc.player.input.leftImpulse != 0.0f);
    }

    public static boolean isInWater() {
        LocalPlayer player = getPlayer();
        return player != null && player.isInWater();
    }

    // TODO: strafe / 加速 / 行走判定等
}
