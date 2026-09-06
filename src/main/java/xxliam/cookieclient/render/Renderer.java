package xxliam.cookieclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import xxliam.cookieclient.render.shader.ShaderFormats;
import xxliam.cookieclient.render.shader.ShaderProgram;
import xxliam.cookieclient.utils.render.ColorUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * 渲染工具类：矩形、圆角、渐变等 2D 绘制入口。
 * <p>
 * 圆角矩形采用「中心矩形 + 四条边 + 四角三角扇」的顶点方案（无需自定义 shader），
 * 在 1.20.1 的 GuiGraphics 渲染流程中稳定可用。
 */
public final class Renderer {

    private Renderer() {
    }

    // ---------------------------------------------------------------------
    // 基础矩形
    // ---------------------------------------------------------------------

    /** 直接使用 GuiGraphics 填充一个直角矩形。 */
    public static void drawRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + height, color);
    }

    /** 以 PoseStack 填充一个直角矩形。 */
    public static void drawRect(PoseStack poseStack, float x, float y, float width, float height, int color) {
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitRect(buffer, matrix, x, y, width, height, color);
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    /** 以 PoseStack 填充一个直角矩形（与 drawRect 等价，命名对齐 OpenZen 的 drawFilledRect）。 */
    public static void drawFilledRect(PoseStack poseStack, float x, float y, float width, float height, int color) {
        drawRect(poseStack, x, y, width, height, color);
    }

    /** 直接向 BufferBuilder 写入一个带颜色的四边形（供批量绘制 ESP 框等使用）。 */
    public static void drawQuad(BufferBuilder builder, Matrix4f matrix, float left, float top, float right, float bottom, Color color) {
        float red = (float) color.getRed() / 255.0f;
        float green = (float) color.getGreen() / 255.0f;
        float blue = (float) color.getBlue() / 255.0f;
        float alpha = (float) color.getAlpha() / 255.0f;
        builder.vertex(matrix, left, bottom, 0.0f).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, right, bottom, 0.0f).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, right, top, 0.0f).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, left, top, 0.0f).color(red, green, blue, alpha).endVertex();
    }

    /** 垂直渐变矩形（顶部 -> 底部）。 */
    public static void drawGradientV(PoseStack poseStack, float x, float y, float width, float height,
                                     int colorTop, int colorBottom) {
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).color(colorTop).endVertex();
        buffer.vertex(matrix, x, y + height, 0.0f).color(colorBottom).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(colorBottom).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0f).color(colorTop).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    /** 水平渐变矩形（左 -> 右）。 */
    public static void drawGradientH(PoseStack poseStack, float x, float y, float width, float height,
                                     int colorLeft, int colorRight) {
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).color(colorLeft).endVertex();
        buffer.vertex(matrix, x, y + height, 0.0f).color(colorLeft).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(colorRight).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0f).color(colorRight).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    /**
     * 高斯模糊阴影 / 发光：预生成模糊纹理并缓存，绘制时用颜色着色。
     * <p>
     * 搬运自 OpenZen 的 {@code RenderUtil.drawShadow}（java.awt 高斯模糊 + 纹理缓存）。
     */
    public static void drawShadow(PoseStack poseStack, float x, float y, float width, float height,
                                  float blurRadius, int color) {
        x -= blurRadius;
        y -= blurRadius;
        width += blurRadius * 2.0f;
        height += blurRadius * 2.0f;
        // 纹理按 GUI scale 超采样生成：羽化渐变在物理分辨率下连续，消除最近邻放大造成的像素感
        float scale = Math.max(1.0F, (float) Minecraft.getInstance().getWindow().getGuiScale());
        int key = (int) (width * height + width * blurRadius) * 31 + Math.round(scale);
        DynamicTexture texture = SHADOW_CACHE.get(key);
        if (texture == null) {
            int w = Math.max((int) Math.ceil(width * scale), 1);
            int h = Math.max((int) Math.ceil(height * scale), 1);
            int r = Math.max((int) Math.ceil(blurRadius * scale), 1);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(255, 255, 255, 255));
            graphics.fillRect(r, r, Math.max(w - r * 2, 1), Math.max(h - r * 2, 1));
            graphics.dispose();
            BufferedImage blurred = new GaussianBlur(blurRadius * scale).filter(image, null);
            texture = createShadowTexture(blurred);
            SHADOW_CACHE.put(key, texture);
        }
        drawTexture(texture.getId(), poseStack, x, y, width, height, color);
    }

    private static void drawTexture(int textureId, PoseStack poseStack, float x, float y, float width, float height, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).color(color).endVertex();
        buffer.vertex(matrix, x, y + height, 0.0f).uv(0.0f, 1.0f).color(color).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0f).uv(1.0f, 1.0f).color(color).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0f).uv(1.0f, 0.0f).color(color).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    /** 把 BufferedImage 上传为 DynamicTexture（不注册进 TextureManager，由缓存持有强引用）。 */
    private static DynamicTexture createShadowTexture(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int a = argb >>> 24 & 0xFF;
                int r = argb >>> 16 & 0xFF;
                int g = argb >>> 8 & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }
        DynamicTexture texture = new DynamicTexture(nativeImage);
        texture.upload();
        // 线性过滤：放大时双线性插值，否则默认最近邻会让羽化边缘呈块状阶梯（像素感）
        texture.setFilter(true, false);
        return texture;
    }

    // ---------------------------------------------------------------------
    // 裁剪（scissor）
    // ---------------------------------------------------------------------

    private static final Stack<int[]> SCISSOR_STACK = new Stack<>();
    private static final Map<Integer, DynamicTexture> SHADOW_CACHE = new HashMap<>();
    private static ShaderProgram ROUNDED_RECT_SHADER;

    // ---------------------------------------------------------------------
    // 后屏模糊（名牌底 frosted 效果；照搬 opal 的 BLUR_PAINT 图层语义）
    // ---------------------------------------------------------------------

    /** 每帧是否已抓取过后屏纹理（由 GuiMixin 在每帧渲染开始时重置）。 */
    private static boolean screenBlurDirty = true;
    private static boolean screenBlurCaptured;
    private static int screenTexId;
    private static int screenTexW = -1;
    private static int screenTexH = -1;
    private static ShaderProgram ROUNDED_TEXTURE_SHADER;

    /** 以 GUI 逻辑坐标裁剪到指定矩形（用于滚动列表等内容裁剪）。 */
    public static void pushScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        int guiScale = (int) mc.getWindow().getGuiScale();
        int scaledX = x * guiScale;
        int scaledY = mc.getWindow().getHeight() - (y + height) * guiScale;
        int scaledWidth = width * guiScale;
        int scaledHeight = height * guiScale;
        int[] parent = SCISSOR_STACK.isEmpty()
                ? new int[]{0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()}
                : SCISSOR_STACK.peek();
        applyScissor(scaledX, scaledY, scaledWidth, scaledHeight, parent);
    }

    /**
     * 推入屏幕像素坐标的 scissor（不再 ×guiScale）。
     * <p>
     * 适用场景：调用方已经在 GUI 逻辑空间里应用过其他非单位缩放（例如 ClickGUI 的整体 Scale 系数），
     * 却又不受 Pose 矩阵影响的裁剪（例如模块列表裁剪）；此时要让 scissor 紧贴实际绘制矩形。
     * 输入的 {@code x/y/width/height} 已经是 GUI 逻辑 × 整体缩放系数，再乘以 {@code getGuiScale()} 就得到屏幕像素。
     */
    public static void pushScissorScreen(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            SCISSOR_STACK.push(new int[]{0, 0, 0, 0});
            RenderSystem.disableScissor();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int guiScale = (int) mc.getWindow().getGuiScale();
        int scaledX = Math.round(x * guiScale);
        int scaledY = mc.getWindow().getHeight() - Math.round((y + height) * guiScale);
        int scaledWidth = Math.round(width * guiScale);
        int scaledHeight = Math.round(height * guiScale);
        int[] parent = SCISSOR_STACK.isEmpty()
                ? new int[]{0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()}
                : SCISSOR_STACK.peek();
        applyScissor(scaledX, scaledY, scaledWidth, scaledHeight, parent);
    }

    /**
     * 把内部 scissor 应用与父级裁剪交集合并到 stack；当与父级完全不相交（width/height 归零）时，
     * 调用 {@link RenderSystem#disableScissor()}，避免 {@code enableScissor(0,y,0,h)} 在部分驱动上
     * 被视作无效 scissor box 而导致内容不被裁剪（即「灰色背景溢出面板」之类的回归）。
     */
    private static void applyScissor(int scaledX, int scaledY, int scaledWidth, int scaledHeight, int[] parent) {
        int clippedX = Math.max(scaledX, parent[0]);
        int clippedY = Math.max(scaledY, parent[1]);
        int clippedWidth = Math.max(0, Math.min(scaledX + scaledWidth, parent[0] + parent[2]) - clippedX);
        int clippedHeight = Math.max(0, Math.min(scaledY + scaledHeight, parent[1] + parent[3]) - clippedY);
        if (clippedWidth <= 0 || clippedHeight <= 0) {
            SCISSOR_STACK.push(new int[]{0, 0, 0, 0});
            RenderSystem.disableScissor();
            return;
        }
        SCISSOR_STACK.push(new int[]{clippedX, clippedY, clippedWidth, clippedHeight});
        RenderSystem.enableScissor(clippedX, clippedY, clippedWidth, clippedHeight);
    }

    public static void popScissor() {
        if (SCISSOR_STACK.isEmpty()) {
            RenderSystem.disableScissor();
            return;
        }
        SCISSOR_STACK.pop();
        if (SCISSOR_STACK.isEmpty()) {
            RenderSystem.disableScissor();
        } else {
            int[] bounds = SCISSOR_STACK.peek();
            RenderSystem.enableScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    // ---------------------------------------------------------------------
    // 圆角矩形
    // ---------------------------------------------------------------------

    /** 绘制统一圆角的圆角矩形（SDF shader 抗锯齿，弧度与 OpenZen 一致）。 */
    public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height,
                                       float radius, int color) {
        drawRoundedRect(poseStack, x, y, width, height, radius, 1.0f, color);
    }

    /** 绘制统一圆角的圆角矩形，带边缘平滑度 smoothness（越大边缘越柔和）。 */
    public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height,
                                       float radius, float smoothness, int color) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        if (ROUNDED_RECT_SHADER == null) {
            ROUNDED_RECT_SHADER = new ShaderProgram("rounded_rect", "vertex_color", ShaderFormats.POSITION_UV_COLOR);
        }
        if (!ROUNDED_RECT_SHADER.isValid()) {
            // shader 不可用（驱动不支持）时回退到三角扇
            drawRoundedRectTessellator(poseStack, x, y, width, height, radius, radius, radius, radius, color);
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        ROUNDED_RECT_SHADER.use();
        GL20.glUniform2f(ROUNDED_RECT_SHADER.getUniformLocation("Size"), width, height);
        GL20.glUniform1f(ROUNDED_RECT_SHADER.getUniformLocation("Radius"), Math.max(radius, 0.0f));
        GL20.glUniform1f(ROUNDED_RECT_SHADER.getUniformLocation("Smoothness"), smoothness);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).color(color).endVertex();
        buffer.vertex(matrix, x, y + height, 0.0f).uv(0.0f, 1.0f).color(color).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0f).uv(1.0f, 1.0f).color(color).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0f).uv(1.0f, 0.0f).color(color).endVertex();
        BufferUploader.draw(buffer.end());
        RenderSystem.disableBlend();
        ROUNDED_RECT_SHADER.stopUsing();
    }

    /** 按 {@link RoundedRectangle} 描述绘制圆角矩形。 */
    public static void drawRoundedRect(PoseStack poseStack, RoundedRectangle rect, int color) {
        drawRoundedRect(poseStack, rect.x1, rect.y1, rect.getWidth(), rect.getHeight(),
                rect.topLeftRadius, rect.topRightRadius, rect.bottomRightRadius, rect.bottomLeftRadius, color);
    }

    /**
     * 绘制四角独立半径的圆角矩形。四角相等时走 SDF shader，否则回退三角扇。
     */
    public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height,
                                       float tl, float tr, float br, float bl, int color) {
        if (tl == tr && tr == br && br == bl) {
            drawRoundedRect(poseStack, x, y, width, height, tl, color);
            return;
        }
        drawRoundedRectTessellator(poseStack, x, y, width, height, tl, tr, br, bl, color);
    }

    private static void drawRoundedRectTessellator(PoseStack poseStack, float x, float y, float width, float height,
                                                   float tl, float tr, float br, float bl, int color) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        float maxRadius = Math.min(width, height) / 2.0f;
        tl = clampRadius(tl, maxRadius);
        tr = clampRadius(tr, maxRadius);
        br = clampRadius(br, maxRadius);
        bl = clampRadius(bl, maxRadius);

        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        // 1) 中心 + 四条边（直角矩形）
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 中心
        emitRect(buffer, matrix, x + tl, y + tl, width - tl - tr, height - tl - bl, color);
        // 上边
        emitRect(buffer, matrix, x + tl, y, width - tl - tr, tl, color);
        // 下边
        emitRect(buffer, matrix, x + bl, y + height - bl, width - bl - br, bl, color);
        // 左边
        emitRect(buffer, matrix, x, y + tl, tl, height - tl - bl, color);
        // 右边
        emitRect(buffer, matrix, x + width - tr, y + tr, tr, height - tr - br, color);
        BufferUploader.drawWithShader(buffer.end());

        // 2) 四个角（三角扇逼近圆弧，作为三角形提交）
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        emitCorner(buffer, matrix, x + tl, y + tl, tl, 180f, 270f, color);           // 左上
        emitCorner(buffer, matrix, x + width - tr, y + tr, tr, 270f, 360f, color);   // 右上
        emitCorner(buffer, matrix, x + width - br, y + height - br, br, 0f, 90f, color); // 右下
        emitCorner(buffer, matrix, x + bl, y + height - bl, bl, 90f, 180f, color);   // 左下
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.disableBlend();
    }

    private static float clampRadius(float radius, float maxRadius) {
        return Math.max(0.0f, Math.min(radius, maxRadius));
    }

    /** 向当前 QUADS buffer 提交一个矩形。 */
    private static void emitRect(BufferBuilder buffer, Matrix4f matrix,
                                 float x, float y, float width, float height, int color) {
        buffer.vertex(matrix, x, y, 0.0f).color(color).endVertex();
        buffer.vertex(matrix, x, y + height, 0.0f).color(color).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(color).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0f).color(color).endVertex();
    }

    /**
     * 向当前 TRIANGLES buffer 提交一个角弧（90° 扇形，以三角形列表表示）。
     *
     * @param startAngle 起始角度（度，屏幕坐标系，y 轴向下）
     * @param endAngle   结束角度
     */
    private static void emitCorner(BufferBuilder buffer, Matrix4f matrix,
                                   float cx, float cy, float radius,
                                   float startAngle, float endAngle, int color) {
        if (radius <= 0.0f) {
            return;
        }
        int segments = Math.max(6, (int) (radius * 2.0f));
        float prevAngle = (float) Math.toRadians(startAngle);
        float prevX = cx + (float) Math.cos(prevAngle) * radius;
        float prevY = cy + (float) Math.sin(prevAngle) * radius;
        for (int i = 1; i <= segments; i++) {
            float angle = (float) Math.toRadians(startAngle + (endAngle - startAngle) * i / segments);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            // 三角形：圆心 -> 上一点 -> 当前点
            buffer.vertex(matrix, cx, cy, 0.0f).color(color).endVertex();
            buffer.vertex(matrix, prevX, prevY, 0.0f).color(color).endVertex();
            buffer.vertex(matrix, x, y, 0.0f).color(color).endVertex();
            prevX = x;
            prevY = y;
        }
    }

    // ---------------------------------------------------------------------
    // 渐变圆角（复刻 OpenOpal NVGRenderer.roundedRectGradient / rectGradient /
    // roundedRectVaryingGradient 的 nvgLinearGradient 端点数学，照搬 opal 原版）
    //
    // opal 端点公式：angle 转单位向量 (dx,dy)，start = 中心 - (dx,dy)*width/2，
    // 线段长 = width；t = 点投影 / width，clamp 0..1 后在 color1→color2 间线性插值。
    // 用「每顶点着色 + 三角形插值」精确复现线性渐变（矩形凸多边形上插值等于线性函数）。
    // ---------------------------------------------------------------------

    /** 统一圆角半径的渐变圆角矩形。angleDegrees 与 opal 一致：0=水平(左→右)，90=垂直(上→下)。 */
    public static void drawRoundedRectGradient(PoseStack poseStack, float x, float y, float width, float height,
                                               float radius, int color1, int color2, float angleDegrees) {
        drawRoundedRectGradient(poseStack, x, y, width, height,
                radius, radius, radius, radius, color1, color2, angleDegrees);
    }

    /** 四角独立半径的渐变圆角矩形（语义同 {@link #drawRoundedRectTessellator} 的多半径版）。 */
    public static void drawRoundedRectGradient(PoseStack poseStack, float x, float y, float width, float height,
                                               float tl, float tr, float br, float bl,
                                               int color1, int color2, float angleDegrees) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        float maxRadius = Math.min(width, height) / 2.0f;
        tl = clampRadius(tl, maxRadius);
        tr = clampRadius(tr, maxRadius);
        br = clampRadius(br, maxRadius);
        bl = clampRadius(bl, maxRadius);

        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // opal nvgLinearGradient 端点
        float angleRad = (float) Math.toRadians(angleDegrees);
        float gdx = (float) Math.cos(angleRad);
        float gdy = (float) Math.sin(angleRad);
        float gsx = x + width * 0.5f - gdx * width * 0.5f; // NVG x0
        float gsy = y + height * 0.5f - gdy * width * 0.5f; // NVG y0（长度轴为 width）
        float gLen = width;

        GradientColorAt cAt = (px, py) -> {
            float t = (((px - gsx) * gdx) + ((py - gsy) * gdy)) / gLen;
            t = Math.max(0.0f, Math.min(1.0f, t));
            return ColorUtil.interpolateColors(color1, color2, t);
        };

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        // 1) 中心 + 四条边（直角矩形）
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitRectGradient(buffer, matrix, x + tl, y + tl, width - tl - tr, height - tl - bl, cAt);      // 中心
        emitRectGradient(buffer, matrix, x + tl, y, width - tl - tr, tl, cAt);                          // 上边
        emitRectGradient(buffer, matrix, x + bl, y + height - bl, width - bl - br, bl, cAt);            // 下边
        emitRectGradient(buffer, matrix, x, y + tl, tl, height - tl - bl, cAt);                          // 左边
        emitRectGradient(buffer, matrix, x + width - tr, y + tr, tr, height - tr - br, cAt);            // 右边
        BufferUploader.drawWithShader(buffer.end());

        // 2) 四个角（三角扇逼近圆弧，作为三角形提交）
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        emitCornerGradient(buffer, matrix, x + tl, y + tl, tl, 180f, 270f, cAt);                        // 左上
        emitCornerGradient(buffer, matrix, x + width - tr, y + tr, tr, 270f, 360f, cAt);                // 右上
        emitCornerGradient(buffer, matrix, x + width - br, y + height - br, br, 0f, 90f, cAt);          // 右下
        emitCornerGradient(buffer, matrix, x + bl, y + height - bl, bl, 90f, 180f, cAt);                // 左下
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.disableBlend();
    }

    /** 向当前 QUADS buffer 提交一个矩形，四顶点各自着色。 */
    private static void emitRectGradient(BufferBuilder buffer, Matrix4f matrix,
                                         float x, float y, float width, float height,
                                         GradientColorAt cAt) {
        buffer.vertex(matrix, x, y, 0.0f).color(cAt.get(x, y)).endVertex();
        buffer.vertex(matrix, x, y + height, 0.0f).color(cAt.get(x, y + height)).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(cAt.get(x + width, y + height)).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0f).color(cAt.get(x + width, y)).endVertex();
    }

    /** 向当前 TRIANGLES buffer 提交一个渐变着色的角弧（90° 扇形）。 */
    private static void emitCornerGradient(BufferBuilder buffer, Matrix4f matrix,
                                           float cx, float cy, float radius,
                                           float startAngle, float endAngle,
                                           GradientColorAt cAt) {
        if (radius <= 0.0f) {
            return;
        }
        int segments = Math.max(6, (int) (radius * 2.0f));
        float prevAngle = (float) Math.toRadians(startAngle);
        float prevX = cx + (float) Math.cos(prevAngle) * radius;
        float prevY = cy + (float) Math.sin(prevAngle) * radius;
        for (int i = 1; i <= segments; i++) {
            float angle = (float) Math.toRadians(startAngle + (endAngle - startAngle) * i / segments);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            buffer.vertex(matrix, cx, cy, 0.0f).color(cAt.get(cx, cy)).endVertex();
            buffer.vertex(matrix, prevX, prevY, 0.0f).color(cAt.get(prevX, prevY)).endVertex();
            buffer.vertex(matrix, x, y, 0.0f).color(cAt.get(x, y)).endVertex();
            prevX = x;
            prevY = y;
        }
    }

    /** 渐变顶点取色函数。 */
    @FunctionalInterface
    private interface GradientColorAt {
        int get(float x, float y);
    }

    // ---------------------------------------------------------------------
    // 后屏模糊（名牌底 frosted 效果；近似 opal BLUR_PAINT 图层）
    // 渲染链：GuiMixin 每帧开头置 dirty -> ESP 需要时 glCopy 主帧缓冲到低分辨率
    // mipmap 纹理（GL 行序 bottom-up）-> 用「圆角裁剪 + 纹理采样」的 shader 画回名牌底。
    // ---------------------------------------------------------------------

    /** GuiMixin 每帧渲染模块前调用：标记本帧尚未抓屏。 */
    public static void markScreenBlurDirty() {
        screenBlurDirty = true;
        screenBlurCaptured = false;
    }

    /**
     * 从当前主帧缓冲拷贝一帧到 RGBA mipmap 纹理（尺寸与帧缓冲一致，采样走 mip 模拟模糊）。
     * 仅当 {@code screenBlurDirty} 时执行一次。
     */
    private static void captureScreenBlurIfNeeded() {
        if (screenBlurCaptured || !screenBlurDirty) {
            return;
        }
        screenBlurDirty = false;
        screenBlurCaptured = true;
        Minecraft mc = Minecraft.getInstance();
        com.mojang.blaze3d.pipeline.RenderTarget target = mc.getMainRenderTarget();
        if (target == null) {
            return;
        }
        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();
        if (fbW <= 0 || fbH <= 0) {
            return;
        }
        try {
            if (screenTexId == 0 || screenTexW != fbW || screenTexH != fbH) {
                if (screenTexId != 0) {
                    GL11.glDeleteTextures(screenTexId);
                }
                screenTexId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, fbW, fbH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
                screenTexW = fbW;
                screenTexH = fbH;
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexId);
            }

            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            target.bindRead();
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fbW, fbH);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            target.unbindRead();
            GL11.glReadBuffer(prevReadBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
        } catch (Exception e) {
            // 抓屏失败静默降级：名牌只画半透明黑底（同 opal 第二层）
            screenBlurCaptured = true;
        }
    }

    /**
     * 用当前后屏模糊纹理填充一个「圆角裁剪」的矩形（名牌背景的模糊层）。
     * <p>
     * @param x/y/w/h  GUI 逻辑坐标矩形
     * @param radius    圆角半径
     * @param lod       采样 mip 层级，越大越模糊（opal 无此参数；推荐 2~3）
     */
    public static void drawScreenBlur(PoseStack poseStack, float x, float y, float w, float h, float radius, float lod) {
        if (w <= 0.0f || h <= 0.0f || screenTexId == 0) {
            return;
        }
        captureScreenBlurIfNeeded();
        if (screenTexId == 0 || screenTexW <= 0 || screenTexH <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float guiScale = (float) mc.getWindow().getGuiScale();
        // GUI 逻辑坐标 -> 帧缓冲物理像素
        float physX = x * guiScale;
        float physY = y * guiScale;
        float physW = w * guiScale;
        float physH = h * guiScale;
        float fbW = screenTexW;
        float fbH = screenTexH;
        // 纹理 v 轴 bottom-up（GL 行序），clip 于帧内
        float u0 = Math.max(0.0f, Math.min(1.0f, physX / fbW));
        float u1 = Math.max(0.0f, Math.min(1.0f, (physX + physW) / fbW));
        float vTop = Math.max(0.0f, Math.min(1.0f, 1.0f - physY / fbH));
        float vBottom = Math.max(0.0f, Math.min(1.0f, 1.0f - (physY + physH) / fbH));

        ensureRoundedTextureShader();
        if (ROUNDED_TEXTURE_SHADER == null || !ROUNDED_TEXTURE_SHADER.isValid()) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        ROUNDED_TEXTURE_SHADER.use();
        GL20.glUniform2f(ROUNDED_TEXTURE_SHADER.getUniformLocation("Size"), w, h);
        GL20.glUniform1f(ROUNDED_TEXTURE_SHADER.getUniformLocation("Radius"), Math.max(radius, 0.0f));
        GL20.glUniform1f(ROUNDED_TEXTURE_SHADER.getUniformLocation("Smoothness"), 1.0f);
        GL20.glUniform1i(ROUNDED_TEXTURE_SHADER.getUniformLocation("ScreenTex"), 0);
        GL20.glUniform4f(ROUNDED_TEXTURE_SHADER.getUniformLocation("Region"), u0, vBottom, u1, vTop);
        GL20.glUniform1f(ROUNDED_TEXTURE_SHADER.getUniformLocation("Lod"), lod);

        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexId);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).uv(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        buffer.vertex(matrix, x, y + h, 0.0f).uv(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        buffer.vertex(matrix, x + w, y + h, 0.0f).uv(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        buffer.vertex(matrix, x + w, y, 0.0f).uv(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        BufferUploader.draw(buffer.end());
        RenderSystem.disableBlend();
        ROUNDED_TEXTURE_SHADER.stopUsing();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
    }

    private static void ensureRoundedTextureShader() {
        if (ROUNDED_TEXTURE_SHADER == null) {
            ROUNDED_TEXTURE_SHADER = new ShaderProgram("rounded_texture", "vertex_color", ShaderFormats.POSITION_UV_COLOR);
        }
    }
}
