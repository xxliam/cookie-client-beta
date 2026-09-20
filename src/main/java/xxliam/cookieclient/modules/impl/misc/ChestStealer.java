package xxliam.cookieclient.modules.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.game.BlockUtil;
import xxliam.cookieclient.utils.game.InventoryUtil;
import xxliam.cookieclient.utils.misc.TickTimeHelper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ChestStealer：自动偷取箱子物品。
 * <p>
 * 移植自 Naven-Modern {@code obsoverlay.modules.impl.misc.ChestStealer}，判定逐条照搬：
 * <ul>
 *   <li>只在打开的箱子界面（{@code ContainerScreen}）里工作，且按标题白名单确认是
 *       单人箱 / 双联箱 / 英文 Chest（{@code Ender Chest} 开关开启时额外放行末影箱）——避免误操作
 *       别人服务器自制的菜单容器；</li>
 *   <li>每件物品先用 {@link #isItemUseful}（比自己身上/背包里的同类更好，或神装，或数量没超上限）
 *       筛一遍，再要求它在箱子里同类中最好（{@link #isBestItemInChest}），才发 QUICK_MOVE 拿进背包；</li>
 *   <li>槽位顺序每次随机打乱，避免固定顺序被反作弊识别；每偷一件后按 {@code Delay (Ticks)} 节流；</li>
 *   <li>箱内已无可拿之物时，节流到点自动 {@code closeContainer()} 关界面。</li>
 * </ul>
 * {@link #isWorking()} 会被 {@link InvManager} 读取：ChestStealer 刚动过手时，
 * 背包管理整刻让路，避免两套槽位操作互相打架。
 * <p>
 * 与 Naven 的差异：Naven 的 {@code TickTimeHelper} 是全局静态自增，cookie 改成实例计时器、
 * 由本模块 {@code onTick()} 推进；因此 {@code isWorking()} 额外要求模块处于开启态，
 * 否则模块关闭后计时器会停在被 reset 的瞬间、永久返回 true 把背包管理卡死。
 */
public class ChestStealer extends Module {

    public static ChestStealer INSTANCE;

    private static final TickTimeHelper TIMER = new TickTimeHelper();

    private final NumberSetting delay = new NumberSetting("Delay (Ticks)", 3.0d, 3.0d, 10.0d, 1.0d);
    private final BooleanSetting pickEnderChest = new BooleanSetting("Ender Chest", false);

    private Screen lastTickScreen;

    public ChestStealer() {
        super("ChestStealer", Category.MISC);
        INSTANCE = this;
        addSetting(delay);
        addSetting(pickEnderChest);
    }

    /** 是否刚执行过偷取（供 {@link InvManager} 让路）。 */
    public static boolean isWorking() {
        return INSTANCE != null && INSTANCE.isEnabled() && !TIMER.delay(3);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        TIMER.tick();

        Screen currentScreen = mc.screen;
        // 1.20.1 的 ContainerScreen 是 AbstractContainerScreen<ChestMenu> 的具体子类，getMenu() 直接就是 ChestMenu
        if (currentScreen instanceof ContainerScreen container) {
            ChestMenu menu = container.getMenu();
            if (currentScreen != this.lastTickScreen) {
                // 刚打开容器：本刻只登记，下一刻才开始动手（照搬 Naven）
                TIMER.reset();
            } else if (this.isChestLike(container, mc)) {
                if (this.isChestEmpty(menu) && TIMER.delay(this.delay.getValue().floatValue())) {
                    mc.player.closeContainer();
                } else {
                    List<Integer> slots = IntStream.range(0, menu.getRowCount() * 9)
                            .boxed().collect(Collectors.toList());
                    Collections.shuffle(slots);
                    for (Integer slotId : slots) {
                        ItemStack stack = menu.getSlot(slotId).getItem();
                        if (isItemUseful(stack) && this.isBestItemInChest(menu, stack)
                                && TIMER.delay(this.delay.getValue().floatValue())) {
                            mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 0,
                                    ClickType.QUICK_MOVE, mc.player);
                            TIMER.reset();
                            break;
                        }
                    }
                }
            }
        }

        this.lastTickScreen = currentScreen;
    }

    /** 标题白名单（照搬 Naven：本地化单人箱/双联箱 + 英文 Chest，末影箱按开关放行）。 */
    @SuppressWarnings("rawtypes")
    private boolean isChestLike(ContainerScreen container, Minecraft mc) {
        String title = container.getTitle().getString();
        if (title.equals(Component.translatable("container.chest").getString())
                || title.equals(Component.translatable("container.chestDouble").getString())
                || title.equals("Chest")) {
            return true;
        }
        return this.pickEnderChest.getValue()
                && title.equals(Component.translatable("container.enderchest").getString());
    }

    /**
     * 该物品在箱子里是否同类最优（照搬 Naven {@code isBestItemInChest}）。
     * <p>
     * 神装与锋利斧直接通过；否则同类比较：盔甲看防护值、剑看伤害、镐/斧/铲看挖掘分，
     * 只要箱子里有更优的同类，这件就不拿。
     */
    private boolean isBestItemInChest(ChestMenu menu, ItemStack stack) {
        if (InventoryUtil.isGodItem(stack) || InventoryUtil.isSharpnessAxe(stack)) {
            return true;
        }
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack checkStack = menu.getSlot(i).getItem();
            if (stack.getItem() instanceof ArmorItem item && checkStack.getItem() instanceof ArmorItem checkItem) {
                if (item.getEquipmentSlot() == checkItem.getEquipmentSlot()
                        && InventoryUtil.getProtection(checkStack) > InventoryUtil.getProtection(stack)) {
                    return false;
                }
            } else if (stack.getItem() instanceof SwordItem && checkStack.getItem() instanceof SwordItem) {
                if (InventoryUtil.getSwordDamage(checkStack) > InventoryUtil.getSwordDamage(stack)) {
                    return false;
                }
            } else if (stack.getItem() instanceof PickaxeItem && checkStack.getItem() instanceof PickaxeItem) {
                if (InventoryUtil.getToolScore(checkStack) > InventoryUtil.getToolScore(stack)) {
                    return false;
                }
            } else if (stack.getItem() instanceof AxeItem && checkStack.getItem() instanceof AxeItem) {
                if (InventoryUtil.getToolScore(checkStack) > InventoryUtil.getToolScore(stack)) {
                    return false;
                }
            } else if (stack.getItem() instanceof ShovelItem && checkStack.getItem() instanceof ShovelItem
                    && InventoryUtil.getToolScore(checkStack) > InventoryUtil.getToolScore(stack)) {
                return false;
            }
        }
        return true;
    }

    /** 箱内已没有「值得拿且同类最优」的物品。 */
    private boolean isChestEmpty(ChestMenu menu) {
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack item = menu.getSlot(i).getItem();
            if (!item.isEmpty() && isItemUseful(item) && this.isBestItemInChest(menu, item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 这件物品是否值得从箱子里拿（照搬 Naven {@code ChestStealer.isItemUseful}）。
     * <p>
     * 神装/锋利斧必拿；盔甲/剑/镐/斧/铲/弩/弓（Punch 或 Power）比「自己已有的最好那件」更好才拿；
     * 指南针只在没有时拿；水桶/岩浆桶/方块/箭矢/雪球蛋/钓竿按 {@link InvManager} 的保留上限，
     * 上限已满则不拿；其余走 {@link InventoryUtil#isCommonItemUseful}（种子/经验瓶之类直接跳过）。
     */
    public static boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (InventoryUtil.isGodItem(stack) || InventoryUtil.isSharpnessAxe(stack)) {
            return true;
        }
        if (stack.getItem() instanceof ArmorItem item) {
            float protection = InventoryUtil.getProtection(stack);
            return !(protection <= InventoryUtil.getBestArmorScore(item.getEquipmentSlot()));
        }
        if (stack.getItem() instanceof SwordItem) {
            return !(InventoryUtil.getSwordDamage(stack) <= InventoryUtil.getBestSwordDamage());
        }
        if (stack.getItem() instanceof PickaxeItem) {
            return !(InventoryUtil.getToolScore(stack) <= InventoryUtil.getBestPickaxeScore());
        }
        if (stack.getItem() instanceof AxeItem) {
            return !(InventoryUtil.getToolScore(stack) <= InventoryUtil.getBestAxeScore());
        }
        if (stack.getItem() instanceof ShovelItem) {
            return !(InventoryUtil.getToolScore(stack) <= InventoryUtil.getBestShovelScore());
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return !(InventoryUtil.getCrossbowScore(stack) <= InventoryUtil.getBestCrossbowScore());
        }
        if (stack.getItem() instanceof BowItem && InventoryUtil.isPunchBow(stack)) {
            return !(InventoryUtil.getPunchBowScore(stack) <= InventoryUtil.getBestPunchBowScore());
        }
        if (stack.getItem() instanceof BowItem && InventoryUtil.isPowerBow(stack)) {
            return !(InventoryUtil.getPowerBowScore(stack) <= InventoryUtil.getBestPowerBowScore());
        }
        if (stack.getItem() == Items.COMPASS) {
            return !InventoryUtil.hasItem(stack.getItem());
        }
        if (stack.getItem() == Items.WATER_BUCKET
                && InventoryUtil.getItemCount(Items.WATER_BUCKET) >= InvManager.getWaterBucketCount()) {
            return false;
        }
        if (stack.getItem() == Items.LAVA_BUCKET
                && InventoryUtil.getItemCount(Items.LAVA_BUCKET) >= InvManager.getLavaBucketCount()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem && BlockUtil.isValidStack(stack)
                && InventoryUtil.getBlockCountInInventory() + stack.getCount()
                        >= InvManager.getMaxBlockSize()) {
            return false;
        }
        if (stack.getItem() == Items.ARROW
                && InventoryUtil.getItemCount(Items.ARROW) + stack.getCount()
                        >= InvManager.getMaxArrowSize()) {
            return false;
        }
        if (stack.getItem() instanceof FishingRodItem && InventoryUtil.getItemCount(Items.FISHING_ROD) >= 1) {
            return false;
        }
        boolean projectile = stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG;
        if (projectile && (InventoryUtil.getItemCount(Items.SNOWBALL) + InventoryUtil.getItemCount(Items.EGG)
                + stack.getCount() >= InvManager.getMaxProjectileSize()
                || !InvManager.shouldKeepProjectile())) {
            return false;
        }
        return !(stack.getItem() instanceof ItemNameBlockItem) && InventoryUtil.isCommonItemUseful(stack);
    }

    /** 后缀 = 偷取间隔（刻），如 3t。 */
    @Override
    public String getSuffix() {
        return delay.getValue().intValue() + "t";
    }
}
