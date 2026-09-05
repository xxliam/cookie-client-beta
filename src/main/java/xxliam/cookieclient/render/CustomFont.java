package xxliam.cookieclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.chars.Char2IntArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import xxliam.cookieclient.utils.math.MathUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 基于 java.awt 的 TTF 自定义字体：按需把字形渲染到纹理图页，再以四边形绘制。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.render.CustomFont}（去 lombok / ClientBase）。
 */
public class CustomFont implements Closeable {

    public record GlyphEntry(float atX, float atY, float r, float g, float b, Glyph toDraw) {
    }

    static class MinecraftColorMap extends Char2IntArrayMap {
        MinecraftColorMap() {
            put('0', 0x000000);
            put('1', 0x0000AA);
            put('2', 0x00AA00);
            put('3', 0x00AAAA);
            put('4', 0xAA0000);
            put('5', 0xAA00AA);
            put('6', 0xFFAA00);
            put('7', 0xAAAAAA);
            put('8', 0x555555);
            put('9', 0x5555FF);
            put('A', 0x55FF55);
            put('B', 0x55FFFF);
            put('C', 0xFF5555);
            put('D', 0xFF55FF);
            put('E', 0xFFFF55);
            put('F', 0xFFFFFF);
        }
    }

    private static final Char2IntArrayMap MC_COLOR_CODES = new MinecraftColorMap();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Color SHADOW_COLOR = new Color(26, 26, 26, 160);

    /**
     * 单个字形的视觉墨迹边界（相对文本原点/基线，已 /scale 归一为逻辑像素）。
     * <p>
     * x/y 为墨迹矩形左上角相对绘制原点的偏移：x 一般 &gt;= 0（左侧留白），
     * y 一般 &lt; 0（baseline 之上的部分）。用于图标等需要"视觉居中"的绘制。
     */
    public record GlyphVisualBounds(float x, float y, float width, float height) {
    }

    private final Object2ObjectMap<Character, GlyphVisualBounds> glyphVisualCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ObjectList<GlyphEntry>> glyphPageMap = new Object2ObjectOpenHashMap<>();
    private final float fontSize;
    private final ObjectList<GlyphPage> glyphPages = new ObjectArrayList<>();
    private final Char2ObjectArrayMap<Glyph> glyphCache = new Char2ObjectArrayMap<>();
    private final int pageSize;
    private final int charsPerPage;
    private final String preloadChars;
    private int scale;
    private Font scaledFont;
    private int guiScaleCache = -1;
    private Future<Void> preloadFuture;
    private boolean initialized;
    private float letterSpacing;
    private FontMetricsImpl fontMetrics;

    public CustomFont(Font font, float fontSize, int pageSize, int charsPerPage, String preloadChars) {
        this.fontSize = fontSize;
        this.pageSize = pageSize;
        this.charsPerPage = charsPerPage;
        this.preloadChars = preloadChars;
        this.fontMetrics = new FontMetricsImpl(font);
        initFont(font, fontSize);
    }

    public CustomFont(Font font, float fontSize) {
        this(font, fontSize, 256, 5, null);
    }

    private static int alignToPageBoundary(int value, int pageSize) {
        return pageSize * (int) Math.floor((double) value / pageSize);
    }

    public static String stripFormatting(String text) {
        char[] chars = text.toCharArray();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chars.length; ++i) {
            char c = chars[i];
            if (c == '\u00a7') {
                ++i;
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private void checkGuiScaleChanged() {
        int guiScale = (int) Minecraft.getInstance().getWindow().getGuiScale();
        if (guiScale != guiScaleCache) {
            close();
            initFont(scaledFont, fontSize);
        }
    }

    private void initFont(Font font, float fontSize) {
        if (initialized) {
            throw new IllegalStateException("Double call to init()");
        }
        initialized = true;
        guiScaleCache = (int) Minecraft.getInstance().getWindow().getGuiScale();
        scale = Math.max(2, guiScaleCache * 2);
        scaledFont = font.deriveFont(fontSize * (float) scale);
        fontMetrics = new FontMetricsImpl(scaledFont);
        if (preloadChars != null && !preloadChars.isEmpty()) {
            preloadFuture = startPreload();
        }
    }

    private Future<Void> startPreload() {
        return EXECUTOR.submit(() -> {
            for (char c : preloadChars.toCharArray()) {
                if (Thread.interrupted()) {
                    break;
                }
                getOrLoadGlyph(c);
            }
            return null;
        });
    }

    private GlyphPage createGlyphPage(char startChar, char endChar) {
        GlyphPage page = new GlyphPage(startChar, endChar, scaledFont, getTempResourceLocation(), charsPerPage);
        glyphPages.add(page);
        return page;
    }

    private Glyph loadGlyph(char c) {
        for (GlyphPage existing : glyphPages) {
            if (existing.contains(c)) {
                return existing.getGlyph(c);
            }
        }
        int pageStart = alignToPageBoundary(c, pageSize);
        GlyphPage page = createGlyphPage((char) pageStart, (char) (pageStart + pageSize));
        return page.getGlyph(c);
    }

    private Glyph getOrLoadGlyph(char c) {
        return glyphCache.computeIfAbsent(c, this::loadGlyph);
    }

    public void drawString(PoseStack poseStack, String text, double x, double y, int color) {
        float r = (float) (color >> 16 & 0xFF) / 255.0f;
        float g = (float) (color >> 8 & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        float a = (float) (color >>> 24 & 0xFF) / 255.0f;
        drawStringRGB(poseStack, text, (float) x, (float) y, r, g, b, a);
    }

    public void drawStringWithShadow(PoseStack poseStack, String text, double x, double y, int color) {
        drawStringColor(poseStack, text, (float) x + 0.5f, (float) y + 0.5f, SHADOW_COLOR);
        drawString(poseStack, text, (float) x, (float) y, color);
    }

    /**
     * 渐变文字（左→右 startColor→endColor），带阴影。复刻 opal 的
     * {@code NVGTextRenderer.drawGradientStringWithShadow} 视觉语义。
     */
    public void drawGradientStringWithShadow(PoseStack poseStack, String text, double x, double y, int startColor, int endColor) {
        drawGradientStringRGB(poseStack, text, (float) x + 0.5f, (float) y + 0.5f,
                startColor, endColor, SHADOW_COLOR);
        drawGradientString(poseStack, text, (float) x, (float) y, startColor, endColor);
    }

    /** 渐变文字（左→右 startColor→endColor），无阴影。 */
    public void drawGradientString(PoseStack poseStack, String text, double x, double y, int startColor, int endColor) {
        drawGradientStringRGB(poseStack, text, (float) x, (float) y, startColor, endColor, null);
    }

    /**
     * 渐变文字底层实现：逐字符把颜色从 startColor 插值到 endColor（按 penX / 文本总宽）。
     * shadowColor 非空时整体作为阴影层绘制（用于 withShadow 的首层）。
     */
    private void drawGradientStringRGB(PoseStack poseStack, String text, float x, float y, int startColor, int endColor, Color shadowColor) {
        if (preloadFuture != null && !preloadFuture.isDone()) {
            try {
                preloadFuture.get();
            } catch (Exception ignored) {
            }
        }
        checkGuiScaleChanged();
        poseStack.pushPose();
        poseStack.translate(MathUtil.round(x, 1), MathUtil.round(--y, 1), 0.0);
        poseStack.scale(1.0f / scale, 1.0f / scale, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f pose = poseStack.last().pose();
        char[] chars = text.toCharArray();
        float totalWidth = getStringWidth(text) * scale;
        float penX = 0.0f;
        float penY = 0.0f;
        boolean inFormatting = false;
        int lineStart = 0;
        boolean isShadow = shadowColor != null;
        float shadowA = isShadow ? (float) shadowColor.getAlpha() / 255.0f : 0.0f;
        float shadowR = isShadow ? (float) shadowColor.getRed() / 255.0f : 0.0f;
        float shadowG = isShadow ? (float) shadowColor.getGreen() / 255.0f : 0.0f;
        float shadowB = isShadow ? (float) shadowColor.getBlue() / 255.0f : 0.0f;
        float alpha = (float) (startColor >>> 24 & 0xFF) / 255.0f;
        synchronized (glyphPageMap) {
            for (int i = 0; i < chars.length; i++) {
                char ch = chars[i];
                if (inFormatting) {
                    inFormatting = false;
                    continue;
                } else if (ch == '\u00a7') {
                    inFormatting = true;
                    continue;
                } else if (ch == '\n') {
                    penY += getStringHeight(text.substring(lineStart, i)) * scale;
                    penX = 0.0f;
                    lineStart = i + 1;
                    continue;
                }
                Glyph glyph = getOrLoadGlyph(ch);
                if (glyph != null) {
                    float t = totalWidth > 0.0f ? Math.min(1.0f, Math.max(0.0f, penX / totalWidth)) : 0.0f;
                    float curR;
                    float curG;
                    float curB;
                    float curA;
                    if (isShadow) {
                        curR = shadowR;
                        curG = shadowG;
                        curB = shadowB;
                        curA = shadowA;
                    } else {
                        curR = ((startColor >> 16 & 0xFF) + (int) (((endColor >> 16 & 0xFF) - (startColor >> 16 & 0xFF)) * t)) / 255.0f;
                        curG = ((startColor >> 8 & 0xFF) + (int) (((endColor >> 8 & 0xFF) - (startColor >> 8 & 0xFF)) * t)) / 255.0f;
                        curB = ((startColor & 0xFF) + (int) (((endColor & 0xFF) - (startColor & 0xFF)) * t)) / 255.0f;
                        curA = alpha;
                    }
                    if (glyph.value() != ' ') {
                        ResourceLocation textureLocation = glyph.owner().textureLocation;
                        glyphPageMap.computeIfAbsent(textureLocation, k -> new ObjectArrayList<>())
                                .add(new GlyphEntry(penX, penY, curR, curG, curB, glyph));
                    }
                    penX += glyph.width() + letterSpacing;
                }
            }
            for (GlyphPage page : glyphPages) {
                page.flush();
            }
            for (ResourceLocation textureLocation : glyphPageMap.keySet()) {
                RenderSystem.setShaderTexture(0, textureLocation);
                List<GlyphEntry> entries = glyphPageMap.get(textureLocation);
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferBuilder = tesselator.getBuilder();
                bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (GlyphEntry entry : entries) {
                    float atX = entry.atX;
                    float atY = entry.atY;
                    Glyph glyph = entry.toDraw;
                    GlyphPage page = glyph.owner();
                    float glyphWidth = glyph.width();
                    float glyphHeight = glyph.height();
                    float u1 = (float) glyph.u() / page.imageWidth;
                    float v1 = (float) glyph.v() / page.imageHeight;
                    float u2 = (float) (glyph.u() + glyph.width()) / page.imageWidth;
                    float v2 = (float) (glyph.v() + glyph.height()) / page.imageHeight;
                    bufferBuilder.vertex(pose, atX, atY + glyphHeight, 0.0f).uv(u1, v2).color(entry.r, entry.g, entry.b, isShadow ? shadowA : alpha).endVertex();
                    bufferBuilder.vertex(pose, atX + glyphWidth, atY + glyphHeight, 0.0f).uv(u2, v2).color(entry.r, entry.g, entry.b, isShadow ? shadowA : alpha).endVertex();
                    bufferBuilder.vertex(pose, atX + glyphWidth, atY, 0.0f).uv(u2, v1).color(entry.r, entry.g, entry.b, isShadow ? shadowA : alpha).endVertex();
                    bufferBuilder.vertex(pose, atX, atY, 0.0f).uv(u1, v1).color(entry.r, entry.g, entry.b, isShadow ? shadowA : alpha).endVertex();
                }
                tesselator.end();
            }
            glyphPageMap.clear();
        }
        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    public void drawStringColor(PoseStack poseStack, String text, double x, double y, Color color) {
        drawStringRGB(poseStack, text, (float) x, (float) y,
                (float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f, (float) color.getBlue() / 255.0f,
                color.getAlpha() / 255.0f);
    }

    public void drawStringRGB(PoseStack poseStack, String text, float x, float y, float r, float g, float b, float a) {
        if (preloadFuture != null && !preloadFuture.isDone()) {
            try {
                preloadFuture.get();
            } catch (Exception ignored) {
            }
        }
        checkGuiScaleChanged();
        float curR = r;
        float curG = g;
        float curB = b;
        poseStack.pushPose();
        poseStack.translate(MathUtil.round(x, 1), MathUtil.round(--y, 1), 0.0);
        poseStack.scale(1.0f / scale, 1.0f / scale, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f pose = poseStack.last().pose();
        char[] chars = text.toCharArray();
        float penX = 0.0f;
        float penY = 0.0f;
        boolean inFormatting = false;
        int lineStart = 0;
        synchronized (glyphPageMap) {
            for (int i = 0; i < chars.length; i++) {
                char ch = chars[i];
                if (inFormatting) {
                    inFormatting = false;
                    char upper = Character.toUpperCase(ch);
                    if (MC_COLOR_CODES.containsKey(upper)) {
                        int[] rgb = colorToRGB(MC_COLOR_CODES.get(upper));
                        curR = rgb[0] / 255.0f;
                        curG = rgb[1] / 255.0f;
                        curB = rgb[2] / 255.0f;
                    } else if (upper == 'R') {
                        curR = r;
                        curG = g;
                        curB = b;
                    }
                } else if (ch == '\u00a7') {
                    inFormatting = true;
                } else if (ch == '\n') {
                    penY += getStringHeight(text.substring(lineStart, i)) * scale;
                    penX = 0.0f;
                    lineStart = i + 1;
                } else {
                    Glyph glyph = getOrLoadGlyph(ch);
                    if (glyph != null) {
                        if (glyph.value() != ' ') {
                            ResourceLocation textureLocation = glyph.owner().textureLocation;
                            glyphPageMap.computeIfAbsent(textureLocation, k -> new ObjectArrayList<>())
                                    .add(new GlyphEntry(penX, penY, curR, curG, curB, glyph));
                        }
                        penX += glyph.width() + letterSpacing;
                    }
                }
            }
            // 统一上传本轮按需渲染的字形纹理（避免逐字符上传）
            for (GlyphPage page : glyphPages) {
                page.flush();
            }
            for (ResourceLocation textureLocation : glyphPageMap.keySet()) {
                RenderSystem.setShaderTexture(0, textureLocation);
                List<GlyphEntry> entries = glyphPageMap.get(textureLocation);
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferBuilder = tesselator.getBuilder();
                bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (GlyphEntry entry : entries) {
                    float atX = entry.atX;
                    float atY = entry.atY;
                    Glyph glyph = entry.toDraw;
                    GlyphPage page = glyph.owner();
                    float glyphWidth = glyph.width();
                    float glyphHeight = glyph.height();
                    float u1 = (float) glyph.u() / page.imageWidth;
                    float v1 = (float) glyph.v() / page.imageHeight;
                    float u2 = (float) (glyph.u() + glyph.width()) / page.imageWidth;
                    float v2 = (float) (glyph.v() + glyph.height()) / page.imageHeight;
                    bufferBuilder.vertex(pose, atX, atY + glyphHeight, 0.0f).uv(u1, v2).color(entry.r, entry.g, entry.b, a).endVertex();
                    bufferBuilder.vertex(pose, atX + glyphWidth, atY + glyphHeight, 0.0f).uv(u2, v2).color(entry.r, entry.g, entry.b, a).endVertex();
                    bufferBuilder.vertex(pose, atX + glyphWidth, atY, 0.0f).uv(u2, v1).color(entry.r, entry.g, entry.b, a).endVertex();
                    bufferBuilder.vertex(pose, atX, atY, 0.0f).uv(u1, v1).color(entry.r, entry.g, entry.b, a).endVertex();
                }
                tesselator.end();
            }
            glyphPageMap.clear();
        }
        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    public void drawStringCentered(PoseStack poseStack, String text, double x, double y, int color) {
        float r = (float) (color >> 16 & 0xFF) / 255.0f;
        float g = (float) (color >> 8 & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        float a = (float) (color >>> 24 & 0xFF) / 255.0f;
        drawStringRGB(poseStack, text, (float) (x - getStringWidth(text) / 2.0f), (float) y, r, g, b, a);
    }

    public void drawStringCenteredColor(PoseStack poseStack, String text, double x, double y, Color color) {
        drawStringRGB(poseStack, text, (float) (x - getStringWidth(text) / 2.0f), (float) y,
                (float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f, (float) color.getBlue() / 255.0f,
                (float) color.getAlpha() / 255.0f);
    }

    /**
     * 取某字符的视觉墨迹边界（逻辑像素，相对绘制原点）。带缓存，仅按需测量一次。
     * <p>
     * 测量 hint 与 {@link GlyphPage} 渲染一致（FRACTIONALMETRICS_ON + GASP 抗锯齿），
     * 与最终绘制结果像素级吻合。x/y 为墨迹矩形左上角相对绘制原点的偏移
     * （x 一般 &gt;= 0 表示左侧留白，y 一般 &lt; 0 表示在 baseline 上方）。
     */
    public GlyphVisualBounds getGlyphVisualBounds(char c) {
        synchronized (glyphVisualCache) {
            GlyphVisualBounds cached = glyphVisualCache.get(c);
            if (cached != null) {
                return cached;
            }
        }
        GlyphVisualBounds measured = measureGlyphVisualBounds(c);
        synchronized (glyphVisualCache) {
            GlyphVisualBounds existing = glyphVisualCache.get(c);
            if (existing != null) {
                return existing;
            }
            glyphVisualCache.put(c, measured);
        }
        return measured;
    }

    private GlyphVisualBounds measureGlyphVisualBounds(char c) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setFont(scaledFont);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
            GlyphVector glyphVector = scaledFont.createGlyphVector(graphics.getFontRenderContext(), new char[]{c});
            Rectangle2D bounds = glyphVector.getVisualBounds();
            float k = 1.0f / scale;
            return new GlyphVisualBounds(
                    (float) bounds.getX() * k,
                    (float) bounds.getY() * k,
                    (float) bounds.getWidth() * k,
                    (float) bounds.getHeight() * k);
        } finally {
            graphics.dispose();
        }
    }

    public float getStringWidth(String text) {
        char[] chars = stripFormatting(text).toCharArray();
        float lineWidth = 0.0f;
        float maxWidth = 0.0f;
        for (char c : chars) {
            if (c == '\n') {
                maxWidth = Math.max(lineWidth, maxWidth);
                lineWidth = 0.0f;
                continue;
            }
            Glyph glyph = getOrLoadGlyph(c);
            lineWidth += (glyph == null ? 0.0f : (float) glyph.width() / scale) + letterSpacing;
        }
        return Math.max(lineWidth, maxWidth);
    }

    public float getStringHeight(String text) {
        char[] chars = stripFormatting(text).toCharArray();
        if (chars.length == 0) {
            chars = new char[]{' '};
        }
        float lineHeight = 0.0f;
        float totalHeight = 0.0f;
        for (char c : chars) {
            if (c == '\n') {
                totalHeight += lineHeight;
                lineHeight = 0.0f;
                continue;
            }
            Glyph glyph = getOrLoadGlyph(c);
            lineHeight = Math.max(glyph == null ? 0.0f : (float) glyph.height() / scale, lineHeight);
        }
        return lineHeight + totalHeight;
    }

    public float getFontHeight() {
        return (float) (fontMetrics.getLeading() + fontMetrics.getAscent() + fontMetrics.getDescent()) / scale;
    }

    public FontMetricsImpl getFontMetrics() {
        return fontMetrics;
    }

    public int getScale() {
        return scale;
    }

    public void setLetterSpacing(float letterSpacing) {
        this.letterSpacing = letterSpacing;
    }

    public void resetLetterSpacing() {
        this.letterSpacing = 0.0f;
    }

    @Override
    public void close() {
        try {
            if (preloadFuture != null && !preloadFuture.isDone() && !preloadFuture.isCancelled()) {
                preloadFuture.cancel(true);
                preloadFuture = null;
            }
            for (GlyphPage page : glyphPages) {
                page.reset();
            }
            glyphPages.clear();
            glyphCache.clear();
            glyphVisualCache.clear();
            initialized = false;
        } catch (Exception ignored) {
        }
    }

    public static ResourceLocation getTempResourceLocation() {
        return new ResourceLocation("cookie-client", "temp/" + generateRandomName());
    }

    private static String generateRandomName() {
        return IntStream.range(0, 32)
                .mapToObj(i -> String.valueOf((char) new Random().nextInt(97, 123)))
                .collect(Collectors.joining());
    }

    public static int[] colorToRGB(int color) {
        return new int[]{color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF};
    }
}
