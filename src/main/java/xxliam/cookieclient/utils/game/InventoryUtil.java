package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BookItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 背包工具（照搬 Naven {@code InventoryUtils}）。
 * <p>
 * 一整套 PVP 装备评分体系：盔甲防护值、剑/斧伤害、工具挖掘分（镐/斧/铲）、
 * 弩/弓（Punch·Power）评分，「神装」识别（锋利 100+ 金斧、击退 2+ 黏液球、图腾、末影水晶），
 * 以及按类取「最好/最差一件」与数量统计。{@code InvManager} / {@code ChestStealer} /
 * {@code AutoTools} 全部建立在这套评分之上。
 * <p>
 * 与 Naven 的差异：Naven 用 {@code private static final Minecraft mc}（类加载即抓取实例），
 * cookie 侧改为方法内取用（{@link #mc()}），避免构造期早于 Minecraft 初始化时抓到 null。
 * 其余判定阈值、权重、返回语义逐条照搬。
 */
public final class InventoryUtil {

    private InventoryUtil() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /**
     * 当前是否处于「菜单/大厅」类场景（Naven：靠物品名里出现长按点击/点击使用/离开游戏等关键词判断），
     * 为 true 时背包类功能应整体停摆。靠中文字串匹配，属 Naven 原逻辑。
     */
    public static boolean shouldDisableFeatures() {
        return getAllItems().stream().anyMatch(item -> {
            if (item.isEmpty()) {
                return false;
            }
            String name = item.getDisplayName().getString();
            return name.contains("长按点击") || name.contains("点击使用") || name.contains("离开游戏")
                    || name.contains("选择一个队伍") || name.contains("再来一局");
        });
    }

    /** 是否为金头（头颅方块，照搬 Naven）。 */
    public static boolean isGoldenHead(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SkullBlock) {
            return true;
        }
        return false;
    }

    /** 「锋利斧」：斧 + 锋利 8~49 级（PVP 附魔斧，照搬 Naven 闭区间）。 */
    public static boolean isSharpnessAxe(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) {
            return false;
        }
        int sharpness = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack);
        return sharpness >= 8 && sharpness < 50;
    }

    /** 「神斧」：金斧 + 锋利 > 100 级。 */
    public static boolean isGodAxe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() == Items.GOLDEN_AXE
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack) > 100;
    }

    public static boolean isEnchantedGApple(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isEndCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    /** 击退球：黏液球 + 击退 > 1 级。 */
    public static boolean isKBBall(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() == Items.SLIME_BALL
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, stack) > 1;
    }

    /** 击退棒：木棍 + 击退 > 1 级。 */
    public static boolean isKBStick(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() == Items.STICK
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, stack) > 1;
    }

    /** 主背包（9~35）第一个空槽位下标，无则 -1。 */
    public static int findEmptyInventory() {
        for (int i = 9; i < mc().player.getInventory().items.size(); i++) {
            if (mc().player.getInventory().items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** 快捷栏（0~8）第一个空槽位下标，无则 -1。 */
    public static int findEmptySlot() {
        for (int i = 0; i < 9; i++) {
            if (mc().player.getInventory().items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static int getPunchLevel(ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
    }

    public static int getPowerLevel(ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
    }

    /** 主背包 + 盔甲槽的全部物品（照搬 Naven：不含副手）。 */
    public static List<ItemStack> getAllItems() {
        ArrayList<ItemStack> list = new ArrayList<>(40);
        list.addAll(mc().player.getInventory().items);
        list.addAll(mc().player.getInventory().armor);
        return list;
    }

    public static float getBestArmorScore(EquipmentSlot slot) {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ArmorItem
                        && ((ArmorItem) item.getItem()).getEquipmentSlot() == slot)
                .map(InventoryUtil::getProtection)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /** 已穿在身上的某部位盔甲防护值。 */
    public static float getCurrentArmorScore(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) {
            return getProtection(mc().player.getInventory().armor.get(3));
        } else if (slot == EquipmentSlot.CHEST) {
            return getProtection(mc().player.getInventory().armor.get(2));
        } else if (slot == EquipmentSlot.LEGS) {
            return getProtection(mc().player.getInventory().armor.get(1));
        } else if (slot == EquipmentSlot.FEET) {
            return getProtection(mc().player.getInventory().armor.get(0));
        }
        return 0.0F;
    }

    public static float getBestSwordDamage() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof SwordItem)
                .map(InventoryUtil::getSwordDamage)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /** 背包里伤害最高的剑；无则 null。 */
    public static ItemStack getBestSword() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof SwordItem)
                .max(Comparator.comparingInt(s -> (int) (getSwordDamage(s) * 100.0F)))
                .orElse(null);
    }

    /** 该 ItemStack 引用在背包中的槽位（照搬 Naven：按引用相等匹配），无则 -1。 */
    public static int getItemStackSlot(ItemStack stack) {
        if (stack == null) {
            return -1;
        }
        for (int i = 0; i < mc().player.getInventory().items.size(); i++) {
            if (mc().player.getInventory().items.get(i) == stack) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 该物品是否「不是服务器给的菜单按钮」（照搬 Naven：按显示名关键词排除玩家头颅、
     * Click/Right/点击/Teleport/使用/传送/再来）。
     */
    public static boolean isItemValid(ItemStack stack) {
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof PlayerHeadItem) {
                return false;
            }
            String name = stack.getDisplayName().getString();
            if (name.contains("Click") || name.contains("Right") || name.contains("点击")
                    || name.contains("Teleport") || name.contains("使用") || name.contains("传送")
                    || name.contains("再来")) {
                return false;
            }
        }
        return true;
    }

    public static int getItemSlot(Item item) {
        for (int i = 0; i < mc().player.getInventory().items.size(); i++) {
            if (mc().player.getInventory().items.get(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /** 数量最多的蛋/雪球；无则 null。 */
    public static ItemStack getBestProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty()
                        && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL)
                        && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getFishingRod() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof FishingRodItem && isItemValid(item))
                .findAny()
                .orElse(null);
    }

    /** 背包内可用搭路方块的总数（判定同 {@link BlockUtil#isValidStack}）。 */
    public static int getBlockCountInInventory() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && BlockUtil.isValidStack(item) && isItemValid(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static ItemStack getWorstProjectile() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty()
                        && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstArrow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ArrowItem && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getWorstBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && BlockUtil.isValidStack(item) && isItemValid(item))
                .min(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static ItemStack getBestBlock() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BlockItem
                        && BlockUtil.isValidStack(item) && isItemValid(item))
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(null);
    }

    public static float getBestPickaxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof PickaxeItem && isItemValid(item))
                .map(InventoryUtil::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPickaxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof PickaxeItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    /** 最好的普通斧（排除锋利斧）。 */
    public static float getBestAxeScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem
                        && !isSharpnessAxe(item) && isItemValid(item))
                .map(InventoryUtil::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    /** 最好的普通斧（排除锋利斧）。 */
    public static ItemStack getBestAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem
                        && !isSharpnessAxe(item) && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    /** 最好的锋利斧（排除神斧），按斧伤害评分。 */
    public static ItemStack getBestShapeAxe() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof AxeItem && isSharpnessAxe(item)
                        && isItemValid(item) && !isGodAxe(item))
                .max(Comparator.comparingInt(s -> (int) (getAxeDamage(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestShovelScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item))
                .map(InventoryUtil::getToolScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestShovel() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getToolScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestCrossbowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .map(InventoryUtil::getCrossbowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestCrossbow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getCrossbowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestPunchBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InventoryUtil::getPunchBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPunchBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPunchBowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static float getBestPowerBowScore() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .map(InventoryUtil::getPowerBowScore)
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    public static ItemStack getBestPowerBow() {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item))
                .max(Comparator.comparingInt(s -> (int) (getPowerBowScore(s) * 100.0F)))
                .orElse(null);
    }

    public static boolean isPunchBow(ItemStack stack) {
        return getPunchBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean isPowerBow(ItemStack stack) {
        return getPowerBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean hasItem(Item checkItem) {
        return getAllItems().stream().anyMatch(item -> !item.isEmpty() && item.getItem() == checkItem);
    }

    public static int getItemCount(Item checkItem) {
        return getAllItems().stream()
                .filter(item -> !item.isEmpty() && item.getItem() == checkItem)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    /** Punch 弓评分：底 10 + 冲击 + 无限 + 火矢 + 力量/10 + 耐久损耗比。 */
    public static float getPunchBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) {
            return 0.0F;
        }
        float valence = 10.0F;
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack);
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack);
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack) / 10.0F;
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    /** Power 弓评分：底 10 + 冲击/10 + 无限 + 火矢 + 力量 + 耐久损耗比。 */
    public static float getPowerBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) {
            return 0.0F;
        }
        float valence = 10.0F;
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack) / 10.0F;
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack);
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack);
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
        return valence + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    /**
     * 工具挖掘分：镐按石头、斧按橡木原木、铲按泥土的挖掘速度，另加效率附魔
     * {@code level × 0.0075}。神装与锋利斧一律 0 分（不参与工具评选）。
     */
    public static float getToolScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isGodItem(stack) || isSharpnessAxe(stack)) {
            return 0.0F;
        }
        float valence;
        if (stack.getItem() instanceof PickaxeItem) {
            valence = stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
        } else if (stack.getItem() instanceof AxeItem) {
            valence = stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
        } else if (stack.getItem() instanceof ShovelItem) {
            valence = stack.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        } else {
            return 0.0F;
        }
        int efficiency = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, stack);
        if (efficiency > 0) {
            valence += efficiency * 0.0075F;
        }
        return valence;
    }

    /** 斧伤害：锋利斧按材质的固定底分 + 锋利附魔加成（照搬 Naven 的手工材质表）。 */
    public static float getAxeDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }
        float valence = 0.0F;
        if (stack.getItem() instanceof AxeItem axe && isSharpnessAxe(stack)) {
            if (axe == Items.WOODEN_AXE) {
                valence += 4.0F;
            } else if (axe == Items.STONE_AXE) {
                valence += 5.0F;
            } else if (axe == Items.IRON_AXE) {
                valence += 6.0F;
            } else if (axe == Items.GOLDEN_AXE) {
                valence += 4.0F;
            } else if (axe == Items.DIAMOND_AXE) {
                valence += 7.0F;
            }
        }
        int sharpness = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack);
        if (sharpness > 0) {
            valence += Enchantments.SHARPNESS.getDamageBonus(sharpness, MobType.UNDEFINED);
        }
        return valence;
    }

    /** 剑伤害：基础伤害 + 1 + 锋利附魔加成。 */
    public static float getSwordDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }
        float valence = 0.0F;
        if (stack.getItem() instanceof SwordItem sword) {
            valence += sword.getDamage() + 1.0F;
        }
        int sharpness = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack);
        if (sharpness > 0) {
            valence += Enchantments.SHARPNESS.getDamageBonus(sharpness, MobType.UNDEFINED);
        }
        return valence;
    }

    /** 盔甲防护值：材质底分（皮革 100 / 锁链 200 / 金 300 / 铁 400 / 钻石 500 / 下界合金 600）+ 保护附魔等级。 */
    public static float getProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }
        int valence = 0;
        if (stack.getItem() instanceof ArmorItem armor) {
            ArmorMaterial material = armor.getMaterial();
            if (material == ArmorMaterials.LEATHER) {
                valence += 100;
            } else if (material == ArmorMaterials.CHAIN) {
                valence += 200;
            } else if (material == ArmorMaterials.IRON) {
                valence += 400;
            } else if (material == ArmorMaterials.GOLD) {
                valence += 300;
            } else if (material == ArmorMaterials.DIAMOND) {
                valence += 500;
            } else if (material == ArmorMaterials.NETHERITE) {
                valence += 600;
            }
        }
        valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, stack);
        return (float) valence;
    }

    /** 弩评分：快速装填 + 多重射击 + 穿透 附魔等级之和。 */
    public static float getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }
        int valence = 0;
        if (stack.getItem() instanceof CrossbowItem) {
            valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, stack);
            valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, stack);
            valence += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, stack);
        }
        return (float) valence;
    }

    /** 「神装」：锋利 100+ 金斧 / 击退 2+ 黏液球 / 不死图腾 / 末影水晶。 */
    public static boolean isGodItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof AxeItem && stack.getItem() == Items.GOLDEN_AXE
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack) > 100) {
            return true;
        }
        if (stack.getItem() == Items.SLIME_BALL
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, stack) > 1) {
            return true;
        }
        return stack.getItem() == Items.TOTEM_OF_UNDYING || stack.getItem() == Items.END_CRYSTAL;
    }

    /** 普通物品是否有保留价值（排除附魔台/蛛网/书/经验瓶/烟花/种子/打火石）。 */
    public static boolean isCommonItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem block) {
            if (block.getBlock() == Blocks.ENCHANTING_TABLE || block.getBlock() == Blocks.COBWEB) {
                return false;
            }
        } else {
            if (item instanceof BookItem || item instanceof ExperienceBottleItem
                    || item instanceof FireworkRocketItem) {
                return false;
            }
            if (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS
                    || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS) {
                return false;
            }
            if (item == Items.FLINT_AND_STEEL) {
                return false;
            }
        }
        return true;
    }
}
