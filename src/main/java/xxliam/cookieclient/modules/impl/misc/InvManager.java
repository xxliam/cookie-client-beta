package xxliam.cookieclient.modules.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.newclickgui.NewClickGui;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.modules.impl.movement.Scaffold;
import xxliam.cookieclient.notification.NotificationType;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.game.BlockUtil;
import xxliam.cookieclient.utils.game.InventoryUtil;
import xxliam.cookieclient.utils.game.MovementUtil;
import xxliam.cookieclient.utils.misc.TickTimeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * InvManager：智能背包管理。
 * <p>
 * 移植自 Naven-Modern {@code obsoverlay.modules.impl.misc.InventoryCleaner}
 * （Naven 侧类名为 InventoryCleaner、{@code @ModuleInfo} 里挂的模块名是 InventoryManager；
 * 本客户端沿用「类名 == 模块名」约定，统一取 {@code InvManager}）。
 * 全部槽位操作走 {@code MultiPlayerGameMode.handleInventoryMouseClick}，即真实容器点击。
 * <p>
 * 功能分五块（开关与阈值逐条照搬 Naven）：
 * <ol>
 *   <li><b>自动穿甲</b>：身上某部位护甲不如背包里最好的 → 先丢旧的、再把最好的 QUICK_MOVE 上身；</li>
 *   <li><b>副手管理</b>：{@code Offhand Items} 可选 金苹果 / 投掷物 / 钓竿 / 方块，自动把同类里
 *       最好的一件换到副手（金苹果走「合并堆叠 → 交换」两步）；</li>
 *   <li><b>槽位自动切换</b>：剑 / 方块 / 镐 / 斧 / 弓(弩或 Power/Punch 弓，可设优先级) / 水桶 /
 *       末影珍珠 / 火球 / 金苹果 / 蛋雪球 / 钓竿，各自带目标槽位，每刻把「背包里最好的那件」换过去；</li>
 *   <li><b>丢弃垃圾</b>：{@code Throw Items} 开启时逐个检查背包，不需要的（见 {@link #isItemUseful}）直接丢；
 *       并按 {@code Keep Water Buckets / Keep Lava Buckets / Max Block Size / Max Arrow Size /
 *       Max Eggs &amp; Snowballs Size} 保留上限，超量丢最差的一件；</li>
 *   <li><b>开背包自动关闭</b>：{@code Inventory Only} 关闭时，若在背包界面里移动或攻击，
 *       自动发容器关闭包（避免被判定为挂机外挂）。</li>
 * </ol>
 * 另有两个前置节流：{@code ChestStealer} 正在工作、或 {@code Scaffold} 开启、或（Inventory Only 语义下）
 * 当前不在背包界面 / 刚停止移动时，本模块整刻跳过。
 * <p>
 * 与 Naven 的差异：①Naven 的 {@code ClickGUI} 判定换成 cookie 的两套 ClickGUI；
 * ②Naven 用 Apache {@code Pair} 组装配置校验表，cookie 换成局部 record；
 * ③「重复槽位配置」的报错走 cookie 的通知系统（一样是 ERROR + 8 秒）。
 */
public class InvManager extends Module {

    public static InvManager INSTANCE;

    private static final TickTimeHelper TIMER = new TickTimeHelper();

    // ---- 节流与前置 ----
    private final NumberSetting delay = new NumberSetting("Delay (Ticks)", 3.0d, 3.0d, 10.0d, 1.0d);
    private final ModeSetting offhandItems = new ModeSetting("Offhand Items",
            "None", "Golden Apple", "Projectile", "Fishing Rod", "Block").withDefault("None");
    private final BooleanSetting autoArmor = new BooleanSetting("Auto Armor", true);
    private final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", true);

    // ---- 槽位自动切换 ----
    private final BooleanSetting switchSword = new BooleanSetting("Switch Sword", true);
    private final NumberSetting swordSlot =
            new NumberSetting("Sword Slot", 1.0d, 1.0d, 9.0d, 1.0d, () -> switchSword.getValue());

    private final BooleanSetting switchBlock =
            new BooleanSetting("Switch Block", true, () -> !offhandItems.is("Block"));
    private final NumberSetting blockSlot = new NumberSetting("Block Slot", 2.0d, 1.0d, 9.0d, 1.0d,
            () -> switchBlock.getValue() && !offhandItems.is("Block"));
    private final NumberSetting maxBlockSize = new NumberSetting("Max Block Size", 256.0d, 64.0d, 512.0d, 64.0d,
            () -> switchBlock.getValue());

    private final BooleanSetting switchPickaxe = new BooleanSetting("Switch Pickaxe", true);
    private final NumberSetting pickaxeSlot =
            new NumberSetting("Pickaxe Slot", 3.0d, 1.0d, 9.0d, 1.0d, () -> switchPickaxe.getValue());

    private final BooleanSetting switchAxe = new BooleanSetting("Switch Axe", true);
    private final NumberSetting axeSlot =
            new NumberSetting("Axe Slot", 4.0d, 1.0d, 9.0d, 1.0d, () -> switchAxe.getValue());

    private final BooleanSetting switchBow = new BooleanSetting("Switch Bow or Crossbow", true);
    private final NumberSetting bowSlot =
            new NumberSetting("Bow Slot", 5.0d, 1.0d, 9.0d, 1.0d, () -> switchBow.getValue());
    private final ModeSetting preferBow = new ModeSetting("Bow Priority",
            "Crossbow", "Power Bow", "Punch Bow").withDefault("Crossbow").withVisibility(() -> switchBow.getValue());
    private final NumberSetting maxArrowSize = new NumberSetting("Max Arrow Size", 256.0d, 64.0d, 512.0d, 64.0d,
            () -> switchBow.getValue());

    private final BooleanSetting switchWaterBucket = new BooleanSetting("Switch Water Bucket", true);
    private final NumberSetting waterBucketSlot = new NumberSetting("Water Bucket Slot", 6.0d, 1.0d, 9.0d, 1.0d,
            () -> switchWaterBucket.getValue());

    private final BooleanSetting switchEnderPearl = new BooleanSetting("Switch Ender Pearl", true);
    private final NumberSetting enderPearlSlot = new NumberSetting("Ender Pearl Slot", 7.0d, 1.0d, 9.0d, 1.0d,
            () -> switchEnderPearl.getValue());

    private final BooleanSetting switchFireball = new BooleanSetting("Switch Fireball", true);
    private final NumberSetting fireballSlot =
            new NumberSetting("Fireball Slot", 8.0d, 1.0d, 9.0d, 1.0d, () -> switchFireball.getValue());

    private final BooleanSetting switchGoldenApple =
            new BooleanSetting("Switch Golden Apple", true, () -> !offhandItems.is("Golden Apple"));
    private final NumberSetting goldenAppleSlot = new NumberSetting("Golden Apple Slot", 9.0d, 1.0d, 9.0d, 1.0d,
            () -> switchGoldenApple.getValue() && !offhandItems.is("Golden Apple"));

    // ---- 丢弃与保留上限 ----
    private final BooleanSetting throwItems = new BooleanSetting("Throw Items", true);
    private final NumberSetting waterBucketCount = new NumberSetting("Keep Water Buckets", 1.0d, 0.0d, 5.0d, 1.0d,
            () -> throwItems.getValue());
    private final NumberSetting lavaBucketCount = new NumberSetting("Keep Lava Buckets", 1.0d, 0.0d, 5.0d, 1.0d,
            () -> throwItems.getValue());

    private final BooleanSetting keepProjectile = new BooleanSetting("Keep Eggs & Snowballs", true);
    private final BooleanSetting switchProjectile = new BooleanSetting("Switch Eggs & Snowballs", false,
            () -> keepProjectile.getValue() && !offhandItems.is("Projectile"));
    private final NumberSetting projectileSlot = new NumberSetting("Eggs & Snowballs Slot", 9.0d, 1.0d, 9.0d, 1.0d,
            () -> switchProjectile.getValue() && keepProjectile.getValue() && !offhandItems.is("Projectile"));
    private final NumberSetting maxProjectileSize =
            new NumberSetting("Max Eggs & Snowballs Size", 64.0d, 16.0d, 256.0d, 16.0d,
                    () -> keepProjectile.getValue());

    private final BooleanSetting switchRod =
            new BooleanSetting("Switch Rod", false, () -> !offhandItems.is("Fishing Rod"));
    private final NumberSetting rodSlot = new NumberSetting("Rod Slot", 9.0d, 1.0d, 9.0d, 1.0d,
            () -> switchRod.getValue() && !offhandItems.is("Fishing Rod"));

    private int noMoveTicks = 0;
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;

    public InvManager() {
        super("InvManager", Category.MISC);
        INSTANCE = this;
        addSetting(delay);
        addSetting(offhandItems);
        addSetting(autoArmor);
        addSetting(inventoryOnly);
        addSetting(switchSword);
        addSetting(swordSlot);
        addSetting(switchBlock);
        addSetting(blockSlot);
        addSetting(maxBlockSize);
        addSetting(switchPickaxe);
        addSetting(pickaxeSlot);
        addSetting(switchAxe);
        addSetting(axeSlot);
        addSetting(switchBow);
        addSetting(bowSlot);
        addSetting(preferBow);
        addSetting(maxArrowSize);
        addSetting(switchWaterBucket);
        addSetting(waterBucketSlot);
        addSetting(switchEnderPearl);
        addSetting(enderPearlSlot);
        addSetting(switchFireball);
        addSetting(fireballSlot);
        addSetting(switchGoldenApple);
        addSetting(goldenAppleSlot);
        addSetting(throwItems);
        addSetting(waterBucketCount);
        addSetting(lavaBucketCount);
        addSetting(keepProjectile);
        addSetting(switchProjectile);
        addSetting(projectileSlot);
        addSetting(maxProjectileSize);
        addSetting(switchRod);
        addSetting(rodSlot);
    }

    // ------------------------------------------------------------------
    // 静态阈值（ChestStealer 的「是否值得偷」判定要读这些）
    // ------------------------------------------------------------------

    public static int getMaxBlockSize() {
        return INSTANCE == null ? 256 : INSTANCE.maxBlockSize.getValue().intValue();
    }

    public static boolean shouldKeepProjectile() {
        return INSTANCE == null || INSTANCE.keepProjectile.getValue();
    }

    public static int getMaxProjectileSize() {
        return INSTANCE == null ? 64 : INSTANCE.maxProjectileSize.getValue().intValue();
    }

    public static int getMaxArrowSize() {
        return INSTANCE == null ? 256 : INSTANCE.maxArrowSize.getValue().intValue();
    }

    public static int getWaterBucketCount() {
        return INSTANCE == null ? 1 : INSTANCE.waterBucketCount.getValue().intValue();
    }

    public static int getLavaBucketCount() {
        return INSTANCE == null ? 1 : INSTANCE.lavaBucketCount.getValue().intValue();
    }

    // ------------------------------------------------------------------
    // 收/发包
    // ------------------------------------------------------------------

    /**
     * 发包含钩（由 {@code ConnectionMixin} 调用，对应 Naven {@code EventPacket} SEND）。
     * <p>
     * {@code Inventory Only} 关闭时，若在背包界面里移动或攻击，自动补一个容器关闭包。
     */
    public static void onPacketSend(Packet<?> packet) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || INSTANCE.inventoryOnly.getValue()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (packet instanceof ServerboundContainerClosePacket) {
            INSTANCE.inventoryOpen = false;
        }
        if (!INSTANCE.inventoryOpen) {
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket) {
            if (MovementUtil.isMoving()) {
                closeContainer(mc);
            }
        } else if (packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundPlayerActionPacket) {
            closeContainer(mc);
        }
    }

    private static void closeContainer(Minecraft mc) {
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
        }
    }

    // ------------------------------------------------------------------
    // 每刻逻辑（Naven EventMotion PRE）
    // ------------------------------------------------------------------

    /** 配置校验项：一个开关 + 它的目标槽位。 */
    private record SlotConfig(BooleanSetting toggle, NumberSetting slot) {
    }

    /** 各开关的目标槽位是否有重复（照搬 Naven {@code checkConfig}）。 */
    private boolean checkConfig() {
        if (!this.keepProjectile.getValue()) {
            this.switchProjectile.setValue(false);
        }
        List<SlotConfig> pairs = new ArrayList<>();
        pairs.add(new SlotConfig(this.switchSword, this.swordSlot));
        pairs.add(new SlotConfig(this.switchPickaxe, this.pickaxeSlot));
        pairs.add(new SlotConfig(this.switchAxe, this.axeSlot));
        pairs.add(new SlotConfig(this.switchBow, this.bowSlot));
        pairs.add(new SlotConfig(this.switchWaterBucket, this.waterBucketSlot));
        pairs.add(new SlotConfig(this.switchEnderPearl, this.enderPearlSlot));
        pairs.add(new SlotConfig(this.switchFireball, this.fireballSlot));
        if (!this.offhandItems.is("Golden Apple")) {
            pairs.add(new SlotConfig(this.switchGoldenApple, this.goldenAppleSlot));
        }
        if (!this.offhandItems.is("Projectile")) {
            pairs.add(new SlotConfig(this.switchProjectile, this.projectileSlot));
        }
        if (!this.offhandItems.is("Fishing Rod")) {
            pairs.add(new SlotConfig(this.switchRod, this.rodSlot));
        }
        if (!this.offhandItems.is("Block")) {
            pairs.add(new SlotConfig(this.switchBlock, this.blockSlot));
        }

        Set<Integer> usedSlot = new HashSet<>();
        for (SlotConfig pair : pairs) {
            if (pair.toggle().getValue()) {
                int targetSlot = pair.slot().getValue().intValue() - 1;
                if (usedSlot.contains(targetSlot)) {
                    return false;
                }
                usedSlot.add(targetSlot);
            }
        }
        return true;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        TIMER.tick();

        if (!(mc.screen instanceof NewClickGui) && !this.checkConfig()) {
            if (CookieClient.NOTIFICATION_MANAGER != null) {
                CookieClient.NOTIFICATION_MANAGER.publish(NotificationType.ERROR, "InvManager",
                        "Duplicate slot config! Please check your config.", 8000);
            }
            this.toggle();
            return;
        }

        if (InventoryUtil.shouldDisableFeatures()) {
            return;
        }

        if (MovementUtil.isMoving()) {
            this.noMoveTicks = 0;
        } else {
            this.noMoveTicks++;
        }

        boolean scaffoldOn = Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled();
        boolean blocked = ChestStealer.isWorking() || scaffoldOn
                || (this.inventoryOnly.getValue() ? !(mc.screen instanceof InventoryScreen) : this.noMoveTicks <= 1);
        if (blocked) {
            this.clickOffHand = false;
            return;
        }

        if (mc.screen instanceof AbstractContainerScreen<?> container
                && container.getMenu().containerId != mc.player.inventoryMenu.containerId) {
            return;
        }

        this.autoArmor(mc);
        this.offhand(mc);
        this.switchItems(mc);
    }

    /** 自动穿甲（照搬 Naven）：先丢身上较差的，再把背包里最好的 QUICK_MOVE 到对应盔甲槽。 */
    private void autoArmor(Minecraft mc) {
        if (!this.autoArmor.getValue()) {
            return;
        }
        for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
            ItemStack stack = mc.player.getInventory().armor.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem item)) {
                continue;
            }
            if (TIMER.delay(this.delay.getValue().floatValue())
                    && InventoryUtil.getBestArmorScore(item.getEquipmentSlot()) > InventoryUtil.getProtection(stack)) {
                // 盔甲槽在容器菜单里的槽位：36~39 分别对应 脚/腿/胸/头
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, 4 + (4 - i), 1,
                        ClickType.THROW, mc.player);
                this.inventoryOpen = true;
                TIMER.reset();
            }
        }

        for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
            ItemStack stack = mc.player.getInventory().items.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem item)) {
                continue;
            }
            float score = InventoryUtil.getProtection(stack);
            boolean isBest = InventoryUtil.getBestArmorScore(item.getEquipmentSlot()) == score;
            boolean isBetter = InventoryUtil.getCurrentArmorScore(item.getEquipmentSlot()) < score;
            if (isBest && isBetter && TIMER.delay(this.delay.getValue().floatValue())) {
                if (i < 9) {
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i + 36, 0,
                            ClickType.QUICK_MOVE, mc.player);
                } else {
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 0,
                            ClickType.QUICK_MOVE, mc.player);
                }
                this.inventoryOpen = true;
                TIMER.reset();
            }
        }
    }

    /** 副手管理（照搬 Naven）：按 {@code Offhand Items} 档位把最好的同类物品换到副手。 */
    private void offhand(Minecraft mc) {
        float delay = this.delay.getValue().floatValue();

        if (this.clickOffHand && TIMER.delay(delay)) {
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, mc.player);
            this.inventoryOpen = true;
            this.clickOffHand = false;
            TIMER.reset();
        }

        if (this.offhandItems.is("Golden Apple")) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            int slot = InventoryUtil.getItemSlot(Items.GOLDEN_APPLE);
            if (slot != -1 && TIMER.delay(delay)) {
                if (offHand.getItem() == Items.GOLDEN_APPLE) {
                    ItemStack goldenAppleStack = mc.player.getInventory().items.get(slot);
                    if (offHand.getCount() + goldenAppleStack.getCount() <= 64) {
                        if (slot < 9) {
                            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot + 36, 0,
                                    ClickType.PICKUP, mc.player);
                        } else {
                            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, 0,
                                    ClickType.PICKUP, mc.player);
                        }
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        TIMER.reset();
                    }
                } else {
                    this.swapOffHand(slot);
                }
            }
        } else if (this.offhandItems.is("Projectile")) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            ItemStack bestProjectile = InventoryUtil.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InventoryUtil.getItemStackSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL
                        || offHand.getCount() < bestProjectile.getCount();
                if (shouldSwap && slot != -1 && TIMER.delay(delay)) {
                    this.swapOffHand(slot);
                }
            }
        } else if (this.offhandItems.is("Fishing Rod")) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            int slot = InventoryUtil.getItemSlot(Items.FISHING_ROD);
            if (slot != -1 && TIMER.delay(delay) && offHand.getItem() != Items.FISHING_ROD) {
                this.swapOffHand(slot);
            }
        } else if (this.offhandItems.is("Block")) {
            ItemStack offHand = mc.player.getInventory().offhand.get(0);
            ItemStack bestBlock = InventoryUtil.getBestBlock();
            if (bestBlock != null) {
                int slot = InventoryUtil.getItemStackSlot(bestBlock);
                boolean shouldSwap;
                if (BlockUtil.isValidStack(offHand)) {
                    shouldSwap = offHand.getCount() < bestBlock.getCount();
                } else {
                    shouldSwap = true;
                }
                if (shouldSwap && slot != -1 && TIMER.delay(delay)) {
                    this.swapOffHand(slot);
                }
            }
        }
    }

    /** 槽位自动切换 + 超量丢弃（照搬 Naven）。 */
    private void switchItems(Minecraft mc) {
        if (this.switchGoldenApple.getValue() && !this.offhandItems.is("Golden Apple")) {
            this.swapItem(this.goldenAppleSlot.getValue().intValue() - 1, Items.GOLDEN_APPLE);
        }

        if (this.switchBlock.getValue()) {
            int blockSlot = this.blockSlot.getValue().intValue() - 1;
            ItemStack currentBlock = mc.player.getInventory().items.get(blockSlot);
            ItemStack bestBlock = InventoryUtil.getBestBlock();
            if (bestBlock != null
                    && (bestBlock.getCount() > currentBlock.getCount() || !BlockUtil.isValidStack(currentBlock))
                    && !this.offhandItems.is("Block")) {
                this.swapItem(blockSlot, bestBlock);
            }
            if ((float) InventoryUtil.getBlockCountInInventory() > this.maxBlockSize.getValue().floatValue()) {
                this.throwItem(InventoryUtil.getWorstBlock());
            }
        }

        if (this.switchSword.getValue()) {
            int slot = this.swordSlot.getValue().intValue() - 1;
            ItemStack currentSword = mc.player.getInventory().items.get(slot);
            ItemStack bestSword = InventoryUtil.getBestSword();
            ItemStack bestShapeAxe = InventoryUtil.getBestShapeAxe();
            if (InventoryUtil.getAxeDamage(bestShapeAxe) > InventoryUtil.getSwordDamage(bestSword)) {
                bestSword = bestShapeAxe;
            }
            if (bestSword != null) {
                float currentDamage = currentSword.getItem() instanceof SwordItem
                        ? InventoryUtil.getSwordDamage(currentSword)
                        : InventoryUtil.getAxeDamage(currentSword);
                float bestDamage = bestSword.getItem() instanceof SwordItem
                        ? InventoryUtil.getSwordDamage(bestSword)
                        : InventoryUtil.getAxeDamage(bestSword);
                if (bestDamage > currentDamage) {
                    this.swapItem(slot, bestSword);
                }
            }
        }

        if (this.switchPickaxe.getValue()) {
            int slot = this.pickaxeSlot.getValue().intValue() - 1;
            ItemStack bestPickaxe = InventoryUtil.getBestPickaxe();
            ItemStack currentPickaxe = mc.player.getInventory().items.get(slot);
            if (bestPickaxe != null && bestPickaxe.getItem() instanceof PickaxeItem
                    && (InventoryUtil.getToolScore(bestPickaxe) > InventoryUtil.getToolScore(currentPickaxe)
                    || !(currentPickaxe.getItem() instanceof PickaxeItem))) {
                this.swapItem(slot, bestPickaxe);
            }
        }

        if (this.switchAxe.getValue()) {
            int slot = this.axeSlot.getValue().intValue() - 1;
            ItemStack bestAxe = InventoryUtil.getBestAxe();
            ItemStack currentAxe = mc.player.getInventory().items.get(slot);
            if (bestAxe != null && bestAxe.getItem() instanceof AxeItem
                    && (InventoryUtil.getToolScore(bestAxe) > InventoryUtil.getToolScore(currentAxe)
                    || !(currentAxe.getItem() instanceof AxeItem))) {
                this.swapItem(slot, bestAxe);
            }
        }

        if (this.switchRod.getValue() && !this.offhandItems.is("Fishing Rod")) {
            int slot = this.rodSlot.getValue().intValue() - 1;
            ItemStack currentRod = mc.player.getInventory().items.get(slot);
            if (!(currentRod.getItem() instanceof FishingRodItem)) {
                this.swapItem(slot, InventoryUtil.getFishingRod());
            }
        }

        if (this.switchBow.getValue()) {
            int slot = this.bowSlot.getValue().intValue() - 1;
            ItemStack currentBow = mc.player.getInventory().items.get(slot);
            ItemStack bestBow;
            float bestBowScore;
            float currentBowScore;
            if (this.preferBow.is("Crossbow")) {
                bestBow = InventoryUtil.getBestCrossbow();
                bestBowScore = InventoryUtil.getCrossbowScore(bestBow);
                currentBowScore = InventoryUtil.getCrossbowScore(currentBow);
            } else if (this.preferBow.is("Power Bow")) {
                bestBow = InventoryUtil.getBestPowerBow();
                bestBowScore = InventoryUtil.getPowerBowScore(bestBow);
                currentBowScore = InventoryUtil.getPowerBowScore(currentBow);
            } else {
                bestBow = InventoryUtil.getBestPunchBow();
                bestBowScore = InventoryUtil.getPunchBowScore(bestBow);
                currentBowScore = InventoryUtil.getPunchBowScore(currentBow);
            }
            // 首选类别没有货时依次降级（照搬 Naven 的三段兜底）
            if (bestBow == null) {
                bestBow = InventoryUtil.getBestCrossbow();
                bestBowScore = InventoryUtil.getCrossbowScore(bestBow);
                currentBowScore = InventoryUtil.getCrossbowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtil.getBestPowerBow();
                bestBowScore = InventoryUtil.getPowerBowScore(bestBow);
                currentBowScore = InventoryUtil.getPowerBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtil.getBestPunchBow();
                bestBowScore = InventoryUtil.getPunchBowScore(bestBow);
                currentBowScore = InventoryUtil.getPunchBowScore(currentBow);
            }
            if (bestBow != null && bestBowScore > currentBowScore) {
                this.swapItem(slot, bestBow);
            }

            if ((float) InventoryUtil.getItemCount(Items.ARROW) > this.maxArrowSize.getValue().floatValue()) {
                this.throwItem(InventoryUtil.getWorstArrow());
            }
        }

        if (this.switchEnderPearl.getValue()) {
            this.swapItem(this.enderPearlSlot.getValue().intValue() - 1, Items.ENDER_PEARL);
        }
        if (this.switchWaterBucket.getValue()) {
            this.swapItem(this.waterBucketSlot.getValue().intValue() - 1, Items.WATER_BUCKET);
        }
        if (this.switchFireball.getValue()) {
            this.swapItem(this.fireballSlot.getValue().intValue() - 1, Items.FIRE_CHARGE);
        }

        if (this.keepProjectile.getValue()) {
            int projectiles = InventoryUtil.getItemCount(Items.EGG) + InventoryUtil.getItemCount(Items.SNOWBALL);
            if ((float) projectiles > this.maxProjectileSize.getValue().floatValue()) {
                this.throwItem(InventoryUtil.getWorstProjectile());
            }
            if (this.switchProjectile.getValue() && !this.offhandItems.is("Projectile")) {
                int slot = this.projectileSlot.getValue().intValue() - 1;
                if (InventoryUtil.getItemCount(Items.EGG) > 0) {
                    this.swapItem(slot, Items.EGG);
                } else if (InventoryUtil.getItemCount(Items.SNOWBALL) > 0) {
                    this.swapItem(slot, Items.SNOWBALL);
                }
            }
        }

        if (this.throwItems.getValue()) {
            List<Integer> slots = IntStream.range(0, mc.player.getInventory().items.size())
                    .boxed().collect(Collectors.toList());
            Collections.shuffle(slots);
            for (Integer slot : slots) {
                ItemStack stack = mc.player.getInventory().items.get(slot);
                if (!stack.isEmpty() && !this.isItemUseful(stack)) {
                    this.throwItem(stack);
                }
            }
        }
    }

    /**
     * 这件物品是否值得留在背包里（照搬 Naven {@code InventoryCleaner.isItemUseful}）。
     * <p>
     * 神装、以及名字带「点击使用」的（服务器菜单道具）一律保留；盔甲/剑/镐/斧/铲/弩/弓
     * 只有「背包里最好的那件」才留；水桶/岩浆桶/钓竿/蛋雪球按保留上限；
     * 其余交给 {@link InventoryUtil#isCommonItemUseful}。
     */
    public boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (InventoryUtil.isGodItem(stack)) {
            return true;
        }
        if (stack.getDisplayName().getString().contains("点击使用")) {
            return true;
        }
        if (stack.getItem() instanceof ArmorItem item) {
            float protection = InventoryUtil.getProtection(stack);
            if (InventoryUtil.getCurrentArmorScore(item.getEquipmentSlot()) >= protection) {
                return false;
            }
            return !(protection < InventoryUtil.getBestArmorScore(item.getEquipmentSlot()));
        }
        if (stack.getItem() instanceof SwordItem) {
            return InventoryUtil.getBestSword() == stack;
        }
        if (stack.getItem() instanceof PickaxeItem) {
            return InventoryUtil.getBestPickaxe() == stack;
        }
        if (stack.getItem() instanceof AxeItem && !InventoryUtil.isSharpnessAxe(stack)) {
            return InventoryUtil.getBestAxe() == stack;
        }
        if (stack.getItem() instanceof ShovelItem) {
            return InventoryUtil.getBestShovel() == stack;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return InventoryUtil.getBestCrossbow() == stack;
        }
        if (stack.getItem() instanceof BowItem && InventoryUtil.isPunchBow(stack)) {
            return InventoryUtil.getBestPunchBow() == stack;
        }
        if (stack.getItem() instanceof BowItem && InventoryUtil.isPowerBow(stack)) {
            return InventoryUtil.getBestPowerBow() == stack;
        }
        if (stack.getItem() instanceof BowItem && InventoryUtil.getItemCount(Items.BOW) > 1) {
            return false;
        }
        if (stack.getItem() == Items.WATER_BUCKET
                && InventoryUtil.getItemCount(Items.WATER_BUCKET) > getWaterBucketCount()) {
            return false;
        }
        if (stack.getItem() == Items.LAVA_BUCKET
                && InventoryUtil.getItemCount(Items.LAVA_BUCKET) > getLavaBucketCount()) {
            return false;
        }
        if (stack.getItem() instanceof FishingRodItem && InventoryUtil.getItemCount(Items.FISHING_ROD) > 1) {
            return false;
        }
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !shouldKeepProjectile()) {
            return false;
        }
        return !(stack.getItem() instanceof ItemNameBlockItem) && InventoryUtil.isCommonItemUseful(stack);
    }

    // ------------------------------------------------------------------
    // 槽位操作原语（照搬 Naven）
    // ------------------------------------------------------------------

    /** 把背包 {@code slot} 的物品换到副手（槽位 40 的 SWAP）。 */
    private void swapOffHand(int slot) {
        if (slot < 9) {
            mcSend(slot + 36, 40, ClickType.SWAP);
        } else {
            mcSend(slot, 40, ClickType.SWAP);
        }
        this.inventoryOpen = true;
        TIMER.reset();
    }

    /** 丢弃一件物品（THROW 单颗）。 */
    private void throwItem(ItemStack item) {
        if (!InventoryUtil.isItemValid(item) || !TIMER.delay(this.delay.getValue().floatValue())) {
            return;
        }
        int itemSlot = InventoryUtil.getItemStackSlot(item);
        if (itemSlot == -1) {
            return;
        }
        if (itemSlot < 9) {
            mcSend(itemSlot + 36, 1, ClickType.THROW);
        } else {
            mcSend(itemSlot, 1, ClickType.THROW);
        }
        this.inventoryOpen = true;
        TIMER.reset();
    }

    /** 把背包里最好的那件（ItemStack 引用）换到 {@code targetSlot}。 */
    private void swapItem(int targetSlot, ItemStack bestItem) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack currentSlot = mc.player.getInventory().items.get(targetSlot);
        if (!InventoryUtil.isItemValid(currentSlot) || bestItem == currentSlot
                || !TIMER.delay(this.delay.getValue().floatValue())) {
            return;
        }
        int bestItemSlot = InventoryUtil.getItemStackSlot(bestItem);
        if (bestItemSlot == -1) {
            return;
        }
        if (bestItemSlot < 9) {
            mcSend(bestItemSlot + 36, targetSlot, ClickType.SWAP);
        } else {
            mcSend(bestItemSlot, targetSlot, ClickType.SWAP);
        }
        this.inventoryOpen = true;
        TIMER.reset();
    }

    /** 按物品种类换：目标槽不是该物品，或数量更少时，把背包里那件换过去。 */
    private void swapItem(int targetSlot, Item item) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack currentSlot = mc.player.getInventory().items.get(targetSlot);
        if (!InventoryUtil.isItemValid(currentSlot) || !TIMER.delay(this.delay.getValue().floatValue())) {
            return;
        }
        int bestItemSlot = InventoryUtil.getItemSlot(item);
        if (bestItemSlot == -1) {
            return;
        }
        ItemStack bestItemStack = mc.player.getInventory().items.get(bestItemSlot);
        if (currentSlot.getItem() != item
                || (currentSlot.getItem() == item && currentSlot.getCount() < bestItemStack.getCount())) {
            if (bestItemSlot < 9) {
                mcSend(bestItemSlot + 36, targetSlot, ClickType.SWAP);
            } else {
                mcSend(bestItemSlot, targetSlot, ClickType.SWAP);
            }
            this.inventoryOpen = true;
            TIMER.reset();
        }
    }

    /** 统一的容器点击发包（点击按钮 0 = 左键）。 */
    private static void mcSend(int menuSlot, int button, ClickType clickType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot, button, clickType, mc.player);
    }
}
