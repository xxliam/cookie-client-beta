package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
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
