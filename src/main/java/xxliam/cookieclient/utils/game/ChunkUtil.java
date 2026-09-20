package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块工具。
 * <p>
 * 照搬 Naven {@code ChunkUtils}：按「有效渲染距离 + 3」的半径遍历玩家周围已加载区块，
 * 用于枚举方块实体（箱子透视等）。Naven 用 {@code Stream.iterate} 手工递推坐标，
 * 这里换成等价的嵌套循环，半径公式与过滤条件（{@code hasChunk}）保持一致。
 */
public final class ChunkUtil {

    private ChunkUtil() {
    }

    /** 半径内所有已加载区块的方块实体（照搬 Naven {@code ChunkUtils.getLoadedBlockEntities}）。 */
    public static List<BlockEntity> getLoadedBlockEntities() {
        List<BlockEntity> result = new ArrayList<>();
        for (LevelChunk chunk : getLoadedChunks()) {
            result.addAll(chunk.getBlockEntities().values());
        }
        return result;
    }

    /** 半径内的已加载区块（照搬 Naven {@code ChunkUtils.getLoadedChunks}）。 */
    public static List<LevelChunk> getLoadedChunks() {
        Minecraft mc = Minecraft.getInstance();
        List<LevelChunk> result = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            return result;
        }
        int radius = Math.max(2, mc.options.getEffectiveRenderDistance()) + 3;
        ChunkPos center = mc.player.chunkPosition();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                if (mc.level.hasChunk(x, z)) {
                    result.add(mc.level.getChunk(x, z));
                }
            }
        }
        return result;
    }
}
