package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 方块工具（对齐 OpenZen 的 BlockUtil，去 ItemUtil 依赖）。
 */
public final class BlockUtil {

    public static final List<Block> blacklist = new ArrayList<>();

    private BlockUtil() {
    }

    public static boolean isEmpty(BlockPos blockPos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockState(blockPos).getBlock() instanceof AirBlock;
    }

    public static BlockState getBlockState(BlockPos blockPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return mc.level.getBlockState(blockPos);
    }

    public static boolean isSolid(BlockState blockState) {
        return isSolid(blockState.getBlock());
    }

    public static boolean isSolid(BlockPos blockPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        return isSolid(mc.level.getBlockState(blockPos).getBlock());
    }

    public static boolean isSolid(Block block) {
        return !(block instanceof LiquidBlock) && !(block instanceof AirBlock);
    }

    /**
     * 方块轮廓形状在世界坐标下的包围盒。
     * <p>
     * 照搬 Naven {@code BlockUtils.getBoundingBox}：{@code getShape(level, pos).bounds().move(pos)}，
     * 即用碰撞/轮廓形状（而非整格 1×1×1），箱子这类非满格方块才贴合。
     */
    public static AABB getBoundingBox(BlockPos blockPos) {
        return getOutlineShape(blockPos).bounds().move(blockPos);
    }

    /** 方块在当前世界的轮廓形状（照搬 Naven {@code BlockUtils.getOutlineShape}）。 */
    public static VoxelShape getOutlineShape(BlockPos blockPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Shapes.empty();
        }
        return getBlockState(blockPos).getShape(mc.level, blockPos);
    }

    /** 该方块是否有可交互轮廓（照搬 Naven {@code BlockUtils.canBeClicked}）。 */
    public static boolean canBeClicked(BlockPos blockPos) {
        return !getOutlineShape(blockPos).isEmpty();
    }

    /**
     * 搭路可用方块的全局黑名单（照搬 Naven {@code Scaffold.blacklistedBlocks}，重复项已去重）。
     */
    public static final List<Block> blacklistedBlocks = Arrays.asList(
            Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.ENCHANTING_TABLE, Blocks.GLASS_PANE,
            Blocks.IRON_BARS, Blocks.SNOW, Blocks.COAL_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.TORCH, Blocks.ANVIL, Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX, Blocks.TNT, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.STONE_BUTTON, Blocks.LEVER, Blocks.TALL_GRASS,
            Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK, Blocks.RAIL, Blocks.CORNFLOWER, Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM, Blocks.VINE, Blocks.SUNFLOWER, Blocks.LADDER, Blocks.FURNACE,
            Blocks.SAND, Blocks.CACTUS, Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTING_TABLE,
            Blocks.COBWEB, Blocks.PUMPKIN, Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH, Blocks.FLOWER_POT);

    /**
     * 是否为「可拿去搭路」的方块堆（照搬 Naven {@code Scaffold.isValidStack}）。
     * <p>
     * 与 {@link #isPlaceable(ItemStack)} 的区别：这里不过滤 {@code WebBlock}（Naven 原逻辑未排除），
     * 但同样排除萤石/菌类/作物/台阶等。ChestStealer / InvManager 的数量统计依赖它。
     */
    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BlockItem) || stack.getCount() <= 1) {
            return false;
        }
        String displayName = stack.getDisplayName().getString();
        if (displayName.contains("Click") || displayName.contains("点击")) {
            return false;
        }
        if (stack.getItem() instanceof ItemNameBlockItem) {
            return false;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof FungusBlock
                || block instanceof CropBlock) {
            return false;
        }
        return !(block instanceof SlabBlock) && !blacklistedBlocks.contains(block);
    }

    /** 是否可用于搭路放置。 */
    public static boolean isPlaceable(ItemStack itemStack) {
        if (itemStack != null && itemStack.getItem() instanceof BlockItem && itemStack.getCount() > 1) {
            if (itemStack.getItem() instanceof ItemNameBlockItem) {
                return false;
            }
            String displayName = itemStack.getDisplayName().getString();
            if (displayName.contains("Click") || displayName.contains("点击")) {
                return false;
            }
            Block block = ((BlockItem) itemStack.getItem()).getBlock();
            if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof FungusBlock
                    || block instanceof CropBlock || block instanceof SlabBlock || block instanceof WebBlock) {
                return false;
            }
            return !blacklist.contains(block);
        }
        return false;
    }
}
