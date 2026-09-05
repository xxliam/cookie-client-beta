package xxliam.cookieclient.gui.mainmenu;

import com.mojang.blaze3d.vertex.PoseStack;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.render.shader.ShaderFormats;
import xxliam.cookieclient.render.shader.ShaderProgram;

/**
 * SkijaUi 图元的 cookie 等价实现：fill / rounded / line / 描边圆环 / 轴向多段线性渐变。
 * <p>
 * 全部以 {@link PoseStack} 为变换上下文，旋转/平移用 pushPose + mulPose 模拟 Canvas 的 save/translate/rotate。
 */
public final class UiDraw {

    private UiDraw() {
    }

    /** SkijaUi.fill：直角矩形（无抗锯齿）。 */
    public static void fill(PoseStack poseStack, float x, float y, float width, float height, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        Renderer.drawRect(poseStack, x, y, width, height, color);
    }

    /** SkijaUi.rounded：统一圆角矩形（抗锯齿）。 */
    public static void rounded(PoseStack poseStack, float x, float y, float width, float height,
                               float radius, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        Renderer.drawRoundedRect(poseStack, x, y, width, height, radius, 1.0F, color);
    }

    /** SkijaUi.line：线宽 antialias 线段。 */
    public static void line(PoseStack poseStack, float x1, float y1, float x2, float y2,
                            float thickness, int color) {
        if (thickness <= 0.0F) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length <= 1.0E-4F) {
            return;
        }
        float nx = -dy / length;
        float ny = dx / length;
        float half = thickness * 0.5F;
        fillQuad(poseStack, x1, y1, x2, y2, nx, ny, half, color);
    }

    private static void fillQuad(PoseStack poseStack, float x1, float y1, float x2, float y2,
                                 float nx, float ny, float half, int color) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShader(
                net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        org.joml.Matrix4f matrix = poseStack.last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, x1 + nx * half, y1 + ny * half, 0.0F).color(color).endVertex();
        buffer.vertex(matrix, x2 + nx * half, y2 + ny * half, 0.0F).color(color).endVertex();
        buffer.vertex(matrix, x2 - nx * half, y2 - ny * half, 0.0F).color(color).endVertex();
        buffer.vertex(matrix, x1 - nx * half, y1 - ny * half, 0.0F).color(color).endVertex();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 描边圆（Skia drawCircle + PaintMode.STROKE）：SDF 抗锯齿环，shader 不可用时回退多边形环。 */
    public static void circle(PoseStack poseStack, float centerX, float centerY,
                              float radius, float strokeWidth, int color) {
        if (radius <= 0.0F || strokeWidth <= 0.0F) {
            return;
        }
        if (!drawSmoothRing(poseStack, centerX, centerY, radius, strokeWidth, color)) {
            float inner = Math.max(0.0F, radius - strokeWidth);
            ring(poseStack, centerX, centerY, inner, radius, color);
        }
    }

    private static ShaderProgram RING_SHADER;

    /** SDF 抗锯齿圆环（quad 覆盖外接正方形，fragment 计算环带距离）。 */
    private static boolean drawSmoothRing(PoseStack poseStack, float centerX, float centerY,
                                          float radius, float strokeWidth, int color) {
        if (RING_SHADER == null) {
            RING_SHADER = new ShaderProgram("ring", "vertex_color", ShaderFormats.POSITION_UV_COLOR);
        }
        if (!RING_SHADER.isValid()) {
            return false;
        }
        float half = strokeWidth * 0.5F;
        float side = (radius + half) * 2.0F + 1.6F;
        float x = centerX - side * 0.5F;
        float y = centerY - side * 0.5F;

        RING_SHADER.use();
        org.lwjgl.opengl.GL20.glUniform2f(RING_SHADER.getUniformLocation("Size"), side, side);
        org.lwjgl.opengl.GL20.glUniform1f(RING_SHADER.getUniformLocation("Radius"), radius);
        org.lwjgl.opengl.GL20.glUniform1f(RING_SHADER.getUniformLocation("Width"), strokeWidth);
        org.lwjgl.opengl.GL20.glUniform1f(RING_SHADER.getUniformLocation("Feather"), 0.7F);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        org.joml.Matrix4f matrix = poseStack.last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, x, y + side, 0.0F).uv(0.0F, 1.0F).color(color).endVertex();
        buffer.vertex(matrix, x + side, y + side, 0.0F).uv(1.0F, 1.0F).color(color).endVertex();
        buffer.vertex(matrix, x + side, y, 0.0F).uv(1.0F, 0.0F).color(color).endVertex();
        buffer.vertex(matrix, x, y, 0.0F).uv(0.0F, 0.0F).color(color).endVertex();
        com.mojang.blaze3d.vertex.BufferUploader.draw(buffer.end());
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        RING_SHADER.stopUsing();
        return true;
    }

    /** 圆环填充（环形带，由若干四边形近似）。 */
    public static void ring(PoseStack poseStack, float centerX, float centerY,
                            float innerRadius, float outerRadius, int color) {
        if (outerRadius <= 0.0F || innerRadius >= outerRadius) {
            return;
        }
        int segments = Math.max(32, (int) (outerRadius * 6.0F));
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShader(
                net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        org.joml.Matrix4f matrix = poseStack.last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        for (int index = 0; index < segments; index++) {
            double a0 = Math.toRadians(360.0 * index / segments);
            double a1 = Math.toRadians(360.0 * (index + 1) / segments);
            float x0 = centerX + (float) Math.cos(a0) * outerRadius;
            float y0 = centerY + (float) Math.sin(a0) * outerRadius;
            float x1 = centerX + (float) Math.cos(a1) * outerRadius;
            float y1 = centerY + (float) Math.sin(a1) * outerRadius;
            float xi0 = centerX + (float) Math.cos(a0) * innerRadius;
            float yi0 = centerY + (float) Math.sin(a0) * innerRadius;
            float xi1 = centerX + (float) Math.cos(a1) * innerRadius;
            float yi1 = centerY + (float) Math.sin(a1) * innerRadius;
            buffer.vertex(matrix, x0, y0, 0.0F).color(color).endVertex();
            buffer.vertex(matrix, x1, y1, 0.0F).color(color).endVertex();
            buffer.vertex(matrix, xi1, yi1, 0.0F).color(color).endVertex();
            buffer.vertex(matrix, xi0, yi0, 0.0F).color(color).endVertex();
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 竖向多段线性渐变（stops 为空时两色均分）。colors/stops 同长；单段退化为两色渐变。 */
    public static void gradientV(PoseStack poseStack, float x, float y, float width, float height,
                                 int[] colors, float[] stops) {
        if (colors == null || colors.length < 2 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        if (stops == null) {
            Renderer.drawGradientV(poseStack, x, y, width, height, colors[0], colors[1]);
            return;
        }
        float[] normalized = normalize(stops);
        for (int index = 0; index < colors.length - 1; index++) {
            float top = y + height * normalized[index];
            float bottom = y + height * normalized[index + 1];
            if (bottom <= top) {
                continue;
            }
            Renderer.drawGradientV(poseStack, x, top, width, bottom - top,
                    colors[index], colors[index + 1]);
        }
    }

    /** 横向多段线性渐变。 */
    public static void gradientH(PoseStack poseStack, float x, float y, float width, float height,
                                 int[] colors, float[] stops) {
        if (colors == null || colors.length < 2 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        if (stops == null) {
            Renderer.drawGradientH(poseStack, x, y, width, height, colors[0], colors[1]);
            return;
        }
        float[] normalized = normalize(stops);
        for (int index = 0; index < colors.length - 1; index++) {
            float left = x + width * normalized[index];
            float right = x + width * normalized[index + 1];
            if (right <= left) {
                continue;
            }
            Renderer.drawGradientH(poseStack, left, y, right - left, height,
                    colors[index], colors[index + 1]);
        }
    }

    private static float[] normalize(float[] stops) {
        if (stops.length >= 2) {
            return stops;
        }
        float[] result = new float[]{0.0F, 1.0F};
        return result;
    }
}
