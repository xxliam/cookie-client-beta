package xxliam.cookieclient.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.utils.game.BlockUtil;
import xxliam.cookieclient.utils.game.ChunkUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;
import xxliam.cookieclient.utils.render.WorldRenderHelper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ChestESP：箱子透视。
 * <p>
 * 移植自 Naven-Modern {@code obsoverlay.modules.impl.render.ChestESP}，逻辑逐条对应：
 * <ul>
 *   <li>每刻（Naven {@code EventMotion} PRE）遍历渲染距离内所有已加载区块的方块实体，
 *       取 {@link ChestBlockEntity} 并计算世界坐标包围盒 —— 双联箱只在大箱（非 {@code LEFT}）
 *       那一格生成，并向连接方向并上另一半；</li>
 *   <li>监听 {@code ClientboundBlockEventPacket}（方块 = 箱子类，b0=1 且 b1=1）记录「已开箱」，
 *       这些箱子改用另一套颜色；</li>
 *   <li>世界渲染阶段（Naven {@code EventRender}）关深度测试 + 混合，用
 *       {@code QUADS}/{@code POSITION} 画半透明实心盒，未开 0.25 透明度。</li>
 * </ul>
 * 与 Naven 的唯一差异是配色：未开箱跟随 {@link ThemeHelper} 主题主色（Naven 为硬编码纯绿
 * {@code (0,1,0)}，如需还原把 {@link #themeColor()} 换成常量即可），已开箱照搬纯红 {@code (1,0,0)}。
 */
public class ChestESP extends Module {

    public static ChestESP INSTANCE;

    /** 已开箱盒色（照搬 Naven {@code openedChestColor}）。 */
    private static final float[] OPENED_CHEST_COLOR = {1.0F, 0.0F, 0.0F};

    /** 盒填充透明度（照搬 Naven {@code 0.25F}）。 */
    private static final float BOX_ALPHA = 0.25F;

    /** 已开箱坐标（Naven {@code openedChests}）。 */
    private final List<BlockPos> openedChests = new CopyOnWriteArrayList<>();

    /** 本刻待渲染的世界坐标包围盒（Naven {@code renderBoundingBoxes}）。 */
    private final List<AABB> renderBoundingBoxes = new CopyOnWriteArrayList<>();

    /** 用于在切换世界（重生/换维度/重开）时清空已开箱记录，对应 Naven 的 {@code EventRespawn} 清表。 */
    private ClientLevel lastLevel;

    public ChestESP() {
        super("ChestESP", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (mc.level != this.lastLevel) {
            this.lastLevel = mc.level;
            this.openedChests.clear();
        }
        this.renderBoundingBoxes.clear();
        for (BlockEntity blockEntity : ChunkUtil.getLoadedBlockEntities()) {
            if (blockEntity instanceof ChestBlockEntity chestBlockEntity) {
                AABB box = this.getChestBox(chestBlockEntity);
                if (box != null) {
                    this.renderBoundingBoxes.add(box);
                }
            }
        }
    }

    @Override
    public void renderWorld(PoseStack poseStack, Camera camera, float partialTicks) {
        if (this.renderBoundingBoxes.isEmpty()) {
            return;
        }
        float[] chestColor = themeColor();
        Vec3 cameraPos = camera.getPosition();
        poseStack.pushPose();
        WorldRenderHelper.beginWorldGeometry();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        for (AABB box : this.renderBoundingBoxes) {
            BlockPos pos = BlockPos.containing(box.minX, box.minY, box.minZ);
            float[] color = this.openedChests.contains(pos) ? OPENED_CHEST_COLOR : chestColor;
            RenderSystem.setShaderColor(color[0], color[1], color[2], BOX_ALPHA);
            WorldRenderHelper.drawSolidBox(bufferBuilder, poseStack.last().pose(), box, cameraPos);
        }
        WorldRenderHelper.endWorldGeometry();
        poseStack.popPose();
    }

    /**
     * 箱子包围盒（照搬 Naven {@code ChestESP.getChestBox}）。
     * <p>
     * {@code LEFT} 那半返回 null（只由另一半代表整只双联箱），非 SINGLE 时向连接方向并上第二半。
     */
    private AABB getChestBox(ChestBlockEntity chestBlockEntity) {
        BlockState state = chestBlockEntity.getBlockState();
        if (!state.hasProperty(ChestBlock.TYPE)) {
            return null;
        }
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.LEFT) {
            return null;
        }
        BlockPos pos = chestBlockEntity.getBlockPos();
        AABB box = BlockUtil.getBoundingBox(pos);
        if (chestType != ChestType.SINGLE) {
            BlockPos pos2 = pos.relative(ChestBlock.getConnectedDirection(state));
            if (BlockUtil.canBeClicked(pos2)) {
                box = box.minmax(BlockUtil.getBoundingBox(pos2));
            }
        }
        return box;
    }

    /** 未开箱盒色的 RGB（主题主色 → 0~1 三通道）。 */
    private static float[] themeColor() {
        int argb = ThemeHelper.getThemeColors()[0];
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F
        };
    }

    /**
     * 收包钩子（由 {@code ConnectionMixin} 调用，对应 Naven {@code EventPacket} RECEIVE）。
     * <p>
     * 箱子开合走 {@code ClientboundBlockEventPacket}：b0==1 且 b1==1 表示单个玩家开启，
     * 记入已开箱列表后该箱改画红盒。
     */
    public static void onPacketReceive(Packet<?> packet) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) {
            return;
        }
        if (packet instanceof ClientboundBlockEventPacket blockEventPacket) {
            boolean isChest = blockEventPacket.getBlock() == Blocks.CHEST
                    || blockEventPacket.getBlock() == Blocks.TRAPPED_CHEST;
            if (isChest && blockEventPacket.getB0() == 1 && blockEventPacket.getB1() == 1) {
                INSTANCE.openedChests.add(blockEventPacket.getPos());
            }
        }
    }
}
