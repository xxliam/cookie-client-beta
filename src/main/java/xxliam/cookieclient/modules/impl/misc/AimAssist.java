package xxliam.cookieclient.modules.impl.misc;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * AimAssist：平滑吸附准星（骨架）。
 */
public class AimAssist extends Module {

    private final NumberSetting speed = new NumberSetting("Speed", 5.0, 0.5, 20.0, 0.5);
    private final NumberSetting range = new NumberSetting("Range", 4.0, 1.0, 8.0, 0.1);
    private final BooleanSetting silent = new BooleanSetting("Silent", false);

    public AimAssist() {
        super("AimAssist", Category.MISC);
        addSetting(speed);
        addSetting(range);
        addSetting(silent);
    }

    @Override
    public void onTick() {
        // TODO: 选目标 -> 计算旋转 -> RotationUtil.smoothRotate
    }
}
