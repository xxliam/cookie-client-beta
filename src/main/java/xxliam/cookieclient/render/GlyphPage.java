package xxliam.cookieclient.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 一个字形图页：按需把字符增量渲染进一张纹理。
 * <p>
 * 不再像原版那样一次性渲染整页 256 个字符 —— 只渲染实际用到的字符，
 * 大幅降低大字号下重建字体的开销（字体 Size 设置调整时尤其受益）。
 */
class GlyphPage {

    /** 图集纹理固定边长（按需填充，足够容纳常规文字）。 */
    private static final int ATLAS_SIZE = 1024;

    final char startChar;
    final char endChar;
    final Font font;
    final ResourceLocation textureLocation;
    final int padding;
    private final Char2ObjectArrayMap<Glyph> glyphMap = new Char2ObjectArrayMap<>();
    int imageWidth;
    int imageHeight;
    boolean uploaded;

    private BufferedImage atlasImage;
    private java.awt.Graphics2D graphics;
    private int nextX;
    private int nextY;
    private int rowHeight;
    private boolean dirty;

    GlyphPage(char startChar, char endChar, Font font, ResourceLocation textureLocation, int padding) {
        this.startChar = startChar;
        this.endChar = endChar;
        this.font = font;
        this.textureLocation = textureLocation;
        this.padding = padding;
    }

    Glyph getGlyph(char c) {
        Glyph glyph = glyphMap.get(c);
        if (glyph == null) {
            glyph = renderChar(c);
        }
        return glyph;
    }

    void reset() {
        Minecraft.getInstance().getTextureManager().release(textureLocation);
        glyphMap.clear();
        imageWidth = -1;
        imageHeight = -1;
        uploaded = false;
        if (graphics != null) {
            graphics.dispose();
        }
        atlasImage = null;
        graphics = null;
        nextX = 0;
        nextY = 0;
        rowHeight = 0;
        dirty = false;
    }

    boolean contains(char c) {
        return c >= startChar && c < endChar;
    }

    /** 自上次上传后有新字符则（重新）上传纹理。 */
    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        if (uploaded) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
        }
        uploadTexture(textureLocation, atlasImage);
        uploaded = true;
    }

    /**
     * 扫描指定 quad 区域内的非透明像素，返回真实墨迹 bbox（相对 quad 左上角，图谱像素）。
     * <p>
     * 用于 {@link CustomFont#getGlyphRenderedBounds(char)}：与 {@code getGlyphVisualBounds}
     * 的 AWT {@code GlyphVector.getVisualBounds} 不同，本方法扫描 {@link GlyphPage} 实际栅格化的
     * 图谱像素，因此与最终屏幕呈现（quad 上屏 + bilinear 下采样）完全一致；
     * 字形经过 GASP+FRACTIONALMETRICS 后可能在 atlas 上呈现的 bbox 略宽或略偏。
     * <p>
     * 若 quad 完全为空（如空白字符、渲染失败）返回 null。
     *
     * @return 长度 4 的数组 [x, y, width, height]（相对 quad 左上角，图谱像素），或 null
     */
    int[] scanGlyphInkBounds(int u, int v, int w, int h) {
        if (atlasImage == null) {
            return null;
        }
        int endX = Math.min(u + w, ATLAS_SIZE);
        int endY = Math.min(v + h, ATLAS_SIZE);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        boolean found = false;
        for (int y = Math.max(0, v); y < endY; y++) {
            for (int x = Math.max(0, u); x < endX; x++) {
                int a = (atlasImage.getRGB(x, y) >>> 24) & 0xFF;
                if (a > 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    found = true;
                }
            }
        }
        if (!found) {
            return null;
        }
        return new int[]{minX - u, minY - v, maxX - minX + 1, maxY - minY + 1};
    }

    /** 暴露当前图谱（仅同包内使用）。 */
    BufferedImage getAtlasImage() {
        return atlasImage;
    }

    private Glyph renderChar(char c) {
        ensureAtlas();
        graphics.setFont(font);
        FontMetrics fontMetrics = graphics.getFontMetrics();
        int ascent = fontMetrics.getAscent();
        int width = fontMetrics.charWidth(c) + padding;
        int height = fontMetrics.getAscent() + fontMetrics.getDescent() + padding;
        if (nextX + width > ATLAS_SIZE) {
            nextX = 0;
            nextY += rowHeight + padding;
            rowHeight = 0;
        }
        if (nextY + height > ATLAS_SIZE) {
            // 超出图集容量（极端情况）：跳过该字符，避免越界绘制
            return null;
        }
        Glyph glyph = new Glyph(nextX, nextY, width, height, c, this);
        graphics.drawString(String.valueOf(c), nextX, nextY + ascent);
        glyphMap.put(c, glyph);
        nextX += width + padding;
        rowHeight = Math.max(rowHeight, height);
        dirty = true;
        return glyph;
    }

    private void ensureAtlas() {
        if (atlasImage == null) {
            atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
            graphics = atlasImage.createGraphics();
            graphics.setColor(new Color(255, 255, 255, 0));
            graphics.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE);
            graphics.setColor(Color.WHITE);
            applyHints(graphics);
            imageWidth = ATLAS_SIZE;
            imageHeight = ATLAS_SIZE;
        }
    }

    private static void applyHints(java.awt.Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void uploadTexture(ResourceLocation location, BufferedImage source) {
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
        RenderSystem.bindTexture(texture.getId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        Minecraft mc = Minecraft.getInstance();
        if (RenderSystem.isOnRenderThread()) {
            mc.getTextureManager().register(location, texture);
        } else {
            RenderSystem.recordRenderCall(() -> mc.getTextureManager().register(location, texture));
        }
    }
}
