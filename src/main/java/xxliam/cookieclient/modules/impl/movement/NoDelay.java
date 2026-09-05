package xxliam.cookieclient.modules.impl.movement;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;

/**
 * NoDelay：无跳跃延迟（连跳时无间隔）。
 * <p>
 * 实际效果由 {@code LivingEntityMixin} 注入 {@code aiStep} 清零跳跃延迟实现。
 */
public class NoDelay extends Module {

    public static NoDelay INSTANCE;
    public final BooleanSetting fastDig = new BooleanSetting("No Jump Delay", true);

    public NoDelay() {
        super("NoDelay", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(fastDig);
    }
}
