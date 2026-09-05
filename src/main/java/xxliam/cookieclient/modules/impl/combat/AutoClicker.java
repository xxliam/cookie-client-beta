package xxliam.cookieclient.modules.impl.combat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import xxliam.cookieclient.mixin.KeyMappingAccessor;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * Auto Clicker —— 移植 OpenOpal {@code AutoClickerModule}（combat）。
 * <p>
 * Opal 侧机制：mixin 把 vanilla 攻击判定里的 attack/use 按键状态替换为 MouseHelper
 * 的模拟状态，点击完全交给 vanilla 执行。1.20.1 等价物是 handleKeybinds 里的
 * {@code while(keyAttack.consumeClick()) startAttack()} 循环——因此这里把对应
 * {@link KeyMapping} 的 clickCount 累加 1（{@link KeyMappingAccessor}），随后本 tick 内
 * vanilla 会像真实鼠标点击一样消费它并触发 startAttack / startUseItem，
 * 挥砍动画、攻击冷却、服务端发包均由原版管线处理，行为与真实点击一致。
 * <p>
 * 语义对照：
 * - Mouse buttons：Left / Right 开关；
 * - CPS（1–20）+ Modern delay（开时忽略 CPS，攻击冷却满才点，1.20.1 等价物为攻击强度刻度）；
 * - Require pressed：仅真实按住对应键时才自动点；
 * - 使用物品（拉弓/进食/格挡等）时暂停。Opal 中 BlockModule 开启可例外允许挥动——
 *   cookie 暂无格挡模块，故一律暂停（BlockModule 移植后在此放开）。
 */
public class AutoClicker extends Module {

    public static AutoClicker INSTANCE;

    public final BooleanSetting left = new BooleanSetting("Left", true);
    public final BooleanSetting right = new BooleanSetting("Right", false);
    public final BooleanSetting modernDelay = new BooleanSetting("Modern Delay", false);
    public final NumberSetting cps = new NumberSetting("CPS", 10.0, 1.0, 20.0, 1.0);
    public final BooleanSetting requirePressed = new BooleanSetting("Require Pressed", true);

    private long nextClickMs;

    public AutoClicker() {
        super("AutoClicker", Category.COMBAT);
        INSTANCE = this;
        addSetting(left);
        addSetting(right);
        addSetting(cps);
        addSetting(modernDelay);
        addSetting(requirePressed);
    }

    @Override
    protected void onEnable() {
        nextClickMs = System.currentTimeMillis();
        super.onEnable();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        // 使用物品中暂停（Opal：除非 BlockModule 允许挥动）
        if (mc.player.isUsingItem()) {
            return;
        }
        if (!swingAvailable()) {
            return;
        }

        HitResult target = mc.hitResult;
        boolean require = requirePressed.getValue();
        if (left.getValue() && target != null && target.getType() != HitResult.Type.BLOCK
                && (!require || mc.options.keyAttack.isDown())) {
            simulateClick(mc.options.keyAttack);
        }
        if (right.getValue() && (!require || mc.options.keyUse.isDown())) {
            simulateClick(mc.options.keyUse);
        }
    }

    private boolean swingAvailable() {
        Minecraft mc = Minecraft.getInstance();
        if (modernDelay.getValue()) {
            // 对应 Opal SwingDelay.isSwingAvailable：1.20.1 攻击冷却 = 攻击强度刻度
            return mc.player != null && mc.player.getAttackStrengthScale(0.5F) >= 1.0F;
        }
        long delay = Math.max(1L, (long) (1000.0 / cps.getValue().doubleValue()));
        long now = System.currentTimeMillis();
        if (now - nextClickMs >= delay) {
            nextClickMs = now;
            return true;
        }
        return false;
    }

    /** 给攻击/使用键累加一次点击计数，vanilla 本 tick 即消费并执行。 */
    private static void simulateClick(KeyMapping keyMapping) {
        if (keyMapping instanceof KeyMappingAccessor accessor) {
            accessor.cookieClient$setClickCount(accessor.cookieClient$getClickCount() + 1);
        }
    }
}
