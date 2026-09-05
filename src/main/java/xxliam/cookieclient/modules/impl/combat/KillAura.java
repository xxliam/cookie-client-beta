package xxliam.cookieclient.modules.impl.combat;

import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * KillAura：自动攻击附近实体（骨架）。
 */
public class KillAura extends Module {

    private final NumberSetting range = new NumberSetting("Range", 3.0, 1.0, 6.0, 0.1);
    private final BooleanSetting silent = new BooleanSetting("Silent", false);

    public KillAura() {
        super("KillAura", Category.COMBAT, GLFW.GLFW_KEY_R);
        addSetting(range);
        addSetting(silent);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }
        // TODO: 遍历实体 -> 选目标 -> 攻击（配合 RotationUtil / TargetManager）
    }
}
