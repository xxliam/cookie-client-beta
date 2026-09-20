package xxliam.cookieclient.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 世界空间 3D 几何绘制（相机相对坐标 + {@code POSITION} 顶点格式 + {@code setShaderColor} 上色）。
 * <p>
 * 约定与 Naven 一致：渲染通道传入的 {@link PoseStack} <b>只含相机旋转</b>（1.20.1 中
 * {@code GameRenderer.renderLevel} 构造、连同相机相对坐标一起交给 {@code LevelRenderer}），
 * 因此这里自行把世界坐标减去相机坐标得到相机相对顶点，二者相乘即视图变换。
 */
public final class WorldRenderHelper {

    private WorldRenderHelper() {
    }

    /**
     * 实心盒（世界坐标）。
     * <p>
     * 逐字照搬 Naven {@code RenderUtils.装女人}：6 个面共 24 个顶点，
     * {@code QUADS} + {@code POSITION}，由调用方通过 {@link RenderSystem#setShaderColor} 上色。
     *
     * @param bufferBuilder 复用的方框缓冲（调用方自行 {@code setShader}）
     * @param matrix        PoseStack 顶矩阵（相机旋转）
     * @param box           世界坐标包围盒
     * @param cameraPos     相机位置，用于换算相机相对坐标
     */
    public static void drawSolidBox(BufferBuilder bufferBuilder, Matrix4f matrix, AABB box, Vec3 cameraPos) {
        float minX = (float) (box.minX - cameraPos.x);
        float minY = (float) (box.minY - cameraPos.y);
        float minZ = (float) (box.minZ - cameraPos.z);
        float maxX = (float) (box.maxX - cameraPos.x);
        float maxY = (float) (box.maxY - cameraPos.y);
        float maxZ = (float) (box.maxZ - cameraPos.z);
        bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.vertex(matrix, minX, minY, minZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, minY, minZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, minX, minY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, minX, maxY, minZ).endVertex();
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).endVertex();
        bufferBuilder.vertex(matrix, minX, minY, minZ).endVertex();
        bufferBuilder.vertex(matrix, minX, maxY, minZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, minY, minZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, minY, minZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, minX, minY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, minX, minY, minZ).endVertex();
        bufferBuilder.vertex(matrix, minX, minY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).endVertex();
        bufferBuilder.vertex(matrix, minX, maxY, minZ).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    /**
     * 实心盒（局部坐标，即盒子坐标已是相机相对量）。
     * <p>
     * 逐字照搬 Naven {@code RenderUtils.drawSolidBox(AABB, PoseStack)}：自建 buffer
     * （{@code QUADS} + {@code POSITION}）并立即提交。
     */
    public static void drawSolidBox(AABB box, PoseStack stack) {
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        Matrix4f matrix = stack.last().pose();
        bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    /**
     * 描边盒（局部坐标）。
     * <p>
     * 逐字照搬 Naven {@code RenderUtils.drawOutlinedBox(AABB, PoseStack)}：自建
     * {@code DEBUG_LINES} + {@code POSITION} buffer 并立即提交。
     */
    public static void drawOutlinedBox(AABB box, PoseStack stack) {
        Matrix4f matrix = stack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        bufferBuilder.begin(Mode.DEBUG_LINES, DefaultVertexFormat.POSITION);
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).endVertex();
        bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    /** 世界渲染通道内的标准着色器/混合/深度开关（照搬 Naven ChestESP.onRender 的前后置）。 */
    public static void beginWorldGeometry() {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionShader);
    }

    /** 与 {@link #beginWorldGeometry()} 成对，恢复深度测试/混合与着色器颜色。 */
    public static void endWorldGeometry() {
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
