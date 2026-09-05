package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 玩家工具：背包操作、物品获取等。
 */
public final class PlayerUtil {

    private PlayerUtil() {
    }

    public static LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    /**
     * 在玩家背包中查找指定物品所在槽位（含副手）。
     *
     * @return 槽位索引，未找到返回 -1
     */
    public static int findSlot(net.minecraft.world.item.Item item) {
        LocalPlayer player = getPlayer();
        if (player == null || item == null) {
            return -1;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }
}
