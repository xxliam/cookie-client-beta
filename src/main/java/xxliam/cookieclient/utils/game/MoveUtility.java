package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;

/**
 * 移动工具（照搬 OpenOpal MoveUtility 中 cookie 需要的部分）。
 */
public final class MoveUtility {

    private MoveUtility() {
    }

    /** 水平移动速度（blocks/second），统计最近一 tick 位移 × 20。照搬 opal。 */
    public static double getBlocksPerSecond() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        double bps = Math.hypot(mc.player.getX() - mc.player.xOld, mc.player.getZ() - mc.player.zOld) * 20.0;
        return Math.round(bps * 100.0) / 100.0;
    }

    public static boolean isMoving() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        return mc.player.zza != 0.0f || mc.player.xxa != 0.0f;
    }
}
