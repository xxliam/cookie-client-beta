package xxliam.cookieclient.modules.impl.world;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;

/**
 * AutoTools：挖掘方块时自动切换到最优工具，挖掘结束切回原槽位。
 * <p>
 * 复刻 OpenZen 的 {@code AutoTools}（Fastest 按挖掘速度、Durability 按剩余耐久）。
 * Silent 的完整静默发包拦截（阻止 {@code SetCarriedItem} 包）留待后续 Mixin 实现。
 */
public class AutoTools extends Module {

    private final ModeSetting priority = new ModeSetting("Priority", "Fastest", "Durability").withDefault("Fastest");
    private final BooleanSetting silent = new BooleanSetting("Silent", false);

    /** 切换前的原始槽位（仅记录第一次，避免连续切换时丢失原始槽位）。 */
    private int previousSlot = -1;

    public AutoTools() {
        super("AutoTools", Category.WORLD);
        addSetting(priority);
        addSetting(silent);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (mc.gameMode.isDestroying()) {
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
                int bestSlot = getBestSlot(pos);
                if (bestSlot != -1 && bestSlot != mc.player.getInventory().selected) {
                    if (previousSlot == -1) {
                        previousSlot = mc.player.getInventory().selected;
                    }
                    mc.player.getInventory().selected = bestSlot;
                }
            }
        } else if (previousSlot != -1) {
            mc.player.getInventory().selected = previousSlot;
            previousSlot = -1;
        }
    }

    private int getBestSlot(BlockPos pos) {
        BlockState state = Minecraft.getInstance().level.getBlockState(pos);
        return priority.is("Durability") ? getBestSlotByDurability(state) : getBestSlotBySpeed(state);
    }

    /** Fastest：选挖掘速度（含效率附魔加成）最高的工具。 */
    private int getBestSlotBySpeed(BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.getItem() instanceof SwordItem) {
                continue;
            }
            float speed = getEffectiveSpeed(stack, state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Durability：在能挖动的前提下，选剩余耐久最高的工具。 */
    private int getBestSlotByDurability(BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        int bestSlot = -1;
        int bestDurability = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.getItem() instanceof SwordItem) {
                continue;
            }
            if (getEffectiveSpeed(stack, state) <= 1.0f) {
                continue;
            }
            int durability = stack.getMaxDamage() - stack.getDamageValue();
            if (durability > bestDurability) {
                bestDurability = durability;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private float getEffectiveSpeed(ItemStack stack, BlockState state) {
        float speed = stack.getItem().getDestroySpeed(stack, state);
        if (speed > 1.0f) {
            int efficiency = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, stack);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1;
            }
        }
        return speed;
    }
}
