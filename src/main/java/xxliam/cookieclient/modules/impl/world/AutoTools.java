package xxliam.cookieclient.modules.impl.world;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.utils.game.InventoryUtil;

/**
 * AutoTools：挖掘方块时自动切换到最优工具。
 * <p>
 * 由 Naven-Modern {@code obsoverlay.modules.impl.misc.AutoTools} 完整替换掉此前的 OpenZen 系实现
 * （原 Priority: Fastest/Durability 两档与 Naven 的算法不同，已整体让位）。
 * <p>
 * 逻辑（照搬 Naven）：
 * <ul>
 *   <li>挖掘中：若 {@code Check Sword} 开启且主手已是剑则不动手；否则取当前准星方块，
 *       在快捷栏 0~8 里挑挖掘速度最高者（速度 &gt; 1 且不是经验矿/红石矿时才计效率附魔
 *       {@code i²+1}），切过去并记下原槽位；</li>
 *   <li>停止挖掘：{@code Switch Back} 开启则切回原槽位；</li>
 *   <li>{@code Silent}：切回时不动真实槽位，而是让<b>手持物渲染</b>仍显示原槽位物品
 *       （由 {@code ItemInHandRendererMixin} 调用 {@link #spoofHeldItem} 实现），
 *       即服务端视角看你在用工具、自己视角看还拿着原物品。</li>
 * </ul>
 * 分类沿用 cookie 既有的 {@code WORLD}（Naven 原为 MISC），避免模块分类位置变动。
 */
public class AutoTools extends Module {

    public static AutoTools INSTANCE;

    private final BooleanSetting checkSword = new BooleanSetting("Check Sword", true);
    private final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);
    private final BooleanSetting silent = new BooleanSetting("Silent", true, () -> switchBack.getValue());

    private int originSlot = -1;

    public AutoTools() {
        super("AutoTools", Category.WORLD);
        INSTANCE = this;
        addSetting(checkSword);
        addSetting(switchBack);
        addSetting(silent);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        // Naven 的 EventMotion PRE 分支（挖掘中）与 POST 分支（停止挖掘）互斥，合成单次每刻判定
        if (mc.gameMode.isDestroying()) {
            if (this.checkSword.getValue() && mc.player.getMainHandItem().getItem() instanceof SwordItem) {
                return;
            }
            if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
                int bestTool = this.getBestTool(pos);
                if (bestTool != -1 && bestTool != mc.player.getInventory().selected) {
                    this.originSlot = mc.player.getInventory().selected;
                    mc.player.getInventory().selected = bestTool;
                }
            }
        } else if (this.switchBack.getValue() && this.originSlot != -1) {
            mc.player.getInventory().selected = this.originSlot;
            this.originSlot = -1;
        }
    }

    /**
     * 静默模式的手持物伪装：返回当前应当渲染在主手的物品。
     * <p>
     * 由 {@code ItemInHandRendererMixin} 在每个渲染帧调用（对应 Naven 的
     * {@code EventUpdateHeldItem} + {@code MixinItemInHandRenderer}）：静默切工具生效期间
     * 仍渲染原槽位物品。
     */
    public static ItemStack spoofHeldItem(ItemStack original) {
        if (INSTANCE == null || !INSTANCE.isEnabled()
                || !INSTANCE.switchBack.getValue() || !INSTANCE.silent.getValue()
                || INSTANCE.originSlot == -1) {
            return original;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return original;
        }
        return mc.player.getInventory().getItem(INSTANCE.originSlot);
    }

    /**
     * 快捷栏里对该方块挖掘最快的工具槽位（照搬 Naven {@code getBestTool}）。
     * <p>
     * 神装（锋利 100+ 金斧等）、剑（蛛网除外）、空槽、空气方块一律跳过；
     * 速度大于 1 且方块不是经验矿/红石矿时，计入效率附魔 {@code i²+1} 加成；
     * 最终没有超过 1.0 速度的候选则返回 -1。
     */
    private int getBestTool(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState blockState = mc.level.getBlockState(pos);
        Block block = blockState.getBlock();
        int slot = 0;
        float bestSpeed = 1.0F;

        for (int index = 0; index < 9; index++) {
            ItemStack itemStack = mc.player.getInventory().getItem(index);
            if (!InventoryUtil.isGodItem(itemStack)
                    && !itemStack.isEmpty()
                    && !blockState.isAir()
                    && (!(itemStack.getItem() instanceof SwordItem) || block instanceof WebBlock)) {
                float speed = itemStack.getItem().getDestroySpeed(itemStack, blockState);
                if (speed > 1.0F && !(block instanceof DropExperienceBlock) && !(block instanceof RedStoneOreBlock)) {
                    int efficiency = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, itemStack);
                    if (efficiency > 0) {
                        speed += efficiency * efficiency + 1;
                    }
                }
                if (speed > bestSpeed) {
                    slot = index;
                    bestSpeed = speed;
                }
            }
        }

        return bestSpeed > 1.0F ? slot : -1;
    }
}
