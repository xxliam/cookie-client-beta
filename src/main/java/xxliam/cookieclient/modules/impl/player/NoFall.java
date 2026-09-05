package xxliam.cookieclient.modules.impl.player;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;

/**
 * NoFall：避免摔落伤害（骨架）。
 */
public class NoFall extends Module {

    private final BooleanSetting packet = new BooleanSetting("Packet", true);

    public NoFall() {
        super("NoFall", Category.PLAYER);
        addSetting(packet);
    }

    @Override
    public void onTick() {
        // TODO: 落地前发送 onGround=true 的移动包
    }
}
