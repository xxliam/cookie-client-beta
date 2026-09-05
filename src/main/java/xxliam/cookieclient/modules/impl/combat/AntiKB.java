package xxliam.cookieclient.modules.impl.combat;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;

/**
 * AntiKB：减小 / 取消受击击退（骨架）。
 */
public class AntiKB extends Module {

    private final BooleanSetting horizontal = new BooleanSetting("Horizontal", true);
    private final BooleanSetting vertical = new BooleanSetting("Vertical", false);

    public AntiKB() {
        super("AntiKB", Category.COMBAT);
        addSetting(horizontal);
        addSetting(vertical);
    }

    @Override
    public void onTick() {
        // TODO: 通过 Mixin 拦截 velocity 修改
    }
}
