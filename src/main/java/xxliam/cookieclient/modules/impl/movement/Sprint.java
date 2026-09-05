package xxliam.cookieclient.modules.impl.movement;

import net.minecraft.client.Minecraft;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;

/**
 * Sprint：自动疾跑。
 * <p>
 * 搬运自 OpenZen 的 {@code shit.zen.modules.impl.movement.Sprint}，
 * 本地化去掉了对 GuiMove / InventoryManager 的依赖，改为在 onTick 中强制疾跑。
 */
public class Sprint extends Module {

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }
        mc.options.toggleSprint().set(false);
        mc.options.keySprint.setDown(true);
    }
}
