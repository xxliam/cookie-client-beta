package xxliam.cookieclient.gui.mainmenu;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 主菜单图像/满屏对角渐变工具。
 * <p>
 * Skia 以物理分辨率渲染，cookie GUI 以缩放单位渲染；为保持清晰度，图片按
 * 目标物理像素（gui 单位 × guiScale）预缩放并缓存，仅窗口/guiScale 变化时重建。
 */
public final class UiImage {

    private static final Map<String, DynamicTexture> TEXTURE_CACHE = new HashMap<>();
    private static final Map<String, BufferedImage> SOURCE_CACHE = new HashMap<>();

    private UiImage() {
    }

    /** 从 classpath 资源解码为 ARGB 位图（缓存源图）。 */
    public static BufferedImage sourceFromResource(String key, String resourcePath) {
        BufferedImage cached = SOURCE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = UiImage.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            BufferedImage image = toArgb(ImageIO.read(stream));
            SOURCE_CACHE.put(key, image);
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 从磁盘路径解码（自定义主菜单背景），失败返回 null。 */
    public static BufferedImage sourceFromFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        String key = file.toAbsolutePath().normalize().toString();
        BufferedImage cached = SOURCE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            BufferedImage image = toArgb(ImageIO.read(file.toFile()));
            if (image != null) {
                SOURCE_CACHE.put(key, image);
            }
            return image;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 居中裁剪（cover）后把图片铺满 (0,0,w,h)（复刻 Skia drawImageRect + MITCHELL 语义的 cover 裁剪）。
     */
    public static void drawCover(PoseStack poseStack, BufferedImage source,
                                 float x, float y, float width, float height) {
        if (source == null || width <= 0.0F || height <= 0.0F) {
            return;
        }
        int texture = cachedCoverTexture(source, width, height);
        if (texture < 0) {
            return;
        }
        drawTexture(poseStack, texture, x, y, width, height, 0xFFFFFFFF);
    }

    /**
     * 全屏对角多段渐变（Skia Shader.makeLinearGradient((0,0)→(w,h), colors, stops)）。
     * 按物理像素栅格化缓存，仅在尺寸变化时重建。
     */
    public static void drawGradientScreen(PoseStack poseStack,
                                          float x, float y, float width, float height,
                                          int[] colors, float[] stops) {
        if (colors == null || colors.length < 2 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        float[] positions = stops == null ? new float[]{0.0F, 1.0F} : stops;
        int texture = cachedGradientTexture(width, height, colors, positions);
        if (texture < 0) {
            return;
        }
        drawTexture(poseStack, texture, x, y, width, height, 0xFFFFFFFF);
    }

    /** 带颜色的纹理四边形（与 Renderer 的私有 drawTexture 同实现）。 */
    public static void drawTexture(PoseStack poseStack, int textureId,
                                   float x, float y, float width, float height, int color) {
        if (textureId < 0 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        float red = (float) (color >> 16 & 0xFF) / 255.0F;
        float green = (float) (color >> 8 & 0xFF) / 255.0F;
        float blue = (float) (color & 0xFF) / 255.0F;
        float alpha = (float) (color >>> 24 & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(matrix, x, y + height, 0.0F).uv(0.0F, 1.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0F).uv(1.0F, 1.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0F).uv(1.0F, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x, y, 0.0F).uv(0.0F, 0.0F).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------------

    private static int cachedCoverTexture(BufferedImage source, float guiWidth, float guiHeight) {
        Minecraft mc = Minecraft.getInstance();
        float guiScale = (float) mc.getWindow().getGuiScale();
        int physicalW = Math.max(1, Math.round(guiWidth * guiScale));
        int physicalH = Math.max(1, Math.round(guiHeight * guiScale));
        String key = coverKey(source, physicalW, physicalH);
        DynamicTexture texture = TEXTURE_CACHE.get(key);
        if (texture != null) {
            return texture.getId();
        }
        BufferedImage scaled = coverCrop(source, physicalW, physicalH);
        if (scaled == null) {
            return -1;
        }
        DynamicTexture created = upload(scaled);
        TEXTURE_CACHE.put(key, created);
        return created.getId();
    }

    /** 居中裁剪 + 高质量缩放到目标物理像素。 */
    private static BufferedImage coverCrop(BufferedImage source, int destW, int destH) {
        float imageWidth = source.getWidth();
        float imageHeight = source.getHeight();
        float viewportAspect = (float) destW / (float) destH;
        float imageAspect = imageWidth / imageHeight;
        float sourceWidth = imageWidth;
        float sourceHeight = imageHeight;
        if (viewportAspect > imageAspect) {
            sourceHeight = imageWidth / viewportAspect;
        } else {
            sourceWidth = imageHeight * viewportAspect;
        }
        int sx = (int) ((imageWidth - sourceWidth) * 0.5F);
        int sy = (int) ((imageHeight - sourceHeight) * 0.5F);
        int cropWidth = Math.min((int) Math.ceil(sourceWidth), source.getWidth() - sx);
        int cropHeight = Math.min((int) Math.ceil(sourceHeight), source.getHeight() - sy);
        BufferedImage cropped = source.getSubimage(sx, sy, cropWidth, cropHeight);
        BufferedImage scaled = new BufferedImage(destW, destH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(cropped, 0, 0, destW, destH, null);
        graphics.dispose();
        return scaled;
    }

    private static int cachedGradientTexture(float guiWidth, float guiHeight,
                                             int[] colors, float[] positions) {
        Minecraft mc = Minecraft.getInstance();
        float guiScale = (float) mc.getWindow().getGuiScale();
        int width = Math.max(1, Math.round(guiWidth * guiScale));
        int height = Math.max(1, Math.round(guiHeight * guiScale));
        String key = "grad|" + width + "x" + height;
        for (int color : colors) {
            key += "|" + color;
        }
        for (float position : positions) {
            key += "|" + position;
        }
        DynamicTexture texture = TEXTURE_CACHE.get(key);
        if (texture != null) {
            return texture.getId();
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        float dx = width;
        float dy = height;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0F) {
            return -1;
        }
        int lastIndex = colors.length - 1;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float t = (px * dx + py * dy) / lengthSquared;
                int color = sampleGradient(colors, positions, t);
                image.setRGB(px, py, color);
            }
        }
        DynamicTexture created = upload(image);
        TEXTURE_CACHE.put(key, created);
        return created.getId();
    }

    private static int sampleGradient(int[] colors, float[] positions, float t) {
        int index;
        for (index = 0; index < positions.length - 1; index++) {
            if (t <= positions[index + 1]) {
                break;
            }
        }
        int from = Math.max(0, index);
        int to = Math.min(colors.length - 1, index + 1);
        float span = positions[to] - positions[from];
        float amount = span <= 0.0F ? 0.0F : (t - positions[from]) / span;
        amount = Math.max(0.0F, Math.min(1.0F, amount));
        return lerpColor(colors[from], colors[to], amount);
    }

    private static int lerpColor(int from, int to, float amount) {
        int alpha = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * amount);
        int red = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * amount);
        int green = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static DynamicTexture upload(BufferedImage source) {
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
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        DynamicTexture texture = new DynamicTexture(nativeImage);
        texture.upload();
        return texture;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source == null) {
            return null;
        }
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return converted;
    }

    private static String coverKey(BufferedImage source, int width, int height) {
        return source.hashCode() + "|" + source.getWidth() + "x" + source.getHeight() + ">" + width + "x" + height;
    }
}
