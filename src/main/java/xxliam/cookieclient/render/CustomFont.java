package xxliam.cookieclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
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
    private final Object2ObjectMap<Character, GlyphVisualBounds> glyphRenderedCache = new Object2ObjectOpenHashMap<>();
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

    /** 文字阴影色（{@link #drawStringWithShadow} 用的那支）的 ARGB，供批量通道复用。 */
    public static int shadowArgb() {
        return SHADOW_COLOR.getRGB();
    }

    // ---------------------------------------------------------------------
    // 批量文字通道（把 N 次 drawString 合并成 1 次 draw 调用）
    // ---------------------------------------------------------------------

    /*
     * 背景：原 {@link #drawStringRGB} 每调用一次就 setShader + begin/end + 一次 draw，
     * 且逐字符 new GlyphEntry 收集后再遍历发射（字符串越长分配越多）。
     * ModuleList 每帧要画 N 行 × 2 遍（阴影 + 主色），于是 N×2 次 draw、N×2×len 个临时对象。
     *
     * 本通道把字形四边形**直接写进调用方持有的缓冲区**：一批内只 setShader 一次、
     * begin/end 一次、draw 一次，且零临时对象。字形图集页按需切换（同页则完全不切换）。
     *
     * 使用约定：beginTextBatch(page) → appendText(...) × N → endTextBatch()。
     * 一批内的文本应尽量同页（如纯 ASCII 的模块名）；若混入其它页，会中途 flush + 换绑，
     * 输出仍然正确，只是多一次 draw。
     */

    /** 批次内当前绑定的图集页。 */
    private ResourceLocation batchPage;
    /** 批次内共用的缓冲区（Tesselator 只有一个，故批次不可嵌套）。 */
    private BufferBuilder batchBuffer;
    private boolean batchOpen;
    /** appendText 复用的矩阵暂存，避免每条文本 new 一个 Matrix4f。 */
    private final Matrix4f batchScratch = new Matrix4f();

    /**
     * 取某段文本所属的字形图集页（按需加载/栅格化字形；无可见字形时返回 null）。
     * <p>
     * 必须在 {@link #beginTextBatch} **之前**调用：它负责把字形准备好，
     * 而 beginTextBatch 里的 flush 才会把新栅格化的字形上传进图谱。
     */
    public ResourceLocation glyphPageFor(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00a7' || ch == '\n' || ch == ' ') {
                continue;
            }
            Glyph glyph = getOrLoadGlyph(ch);
            if (glyph != null && glyph.value() != ' ') {
                return glyph.owner().textureLocation;
            }
        }
        return null;
    }

    /** 开始一批文字（page 由 {@link #glyphPageFor} 取得；可为 null = 无可见字形）。 */
    public void beginTextBatch(ResourceLocation page) {
        if (batchOpen) {
            throw new IllegalStateException("文字批次不可嵌套");
        }
        checkGuiScaleChanged();
        flushDirtyPages();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (page != null) {
            RenderSystem.setShaderTexture(0, page);
        }
        batchPage = page;
        batchBuffer = Tesselator.getInstance().getBuilder();
        batchBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        batchOpen = true;
    }

    /** 上传所有 dirty 图谱页；返回是否有页被重建（重建后需重新绑定纹理）。 */
    private boolean flushDirtyPages() {
        boolean rebuilt = false;
        for (GlyphPage glyphPage : glyphPages) {
            rebuilt |= glyphPage.flush();
        }
        return rebuilt;
    }

    /**
     * 追加一段文字到当前批次（语义与 {@link #drawStringRGB} 一致：x 为左缘、y 为字形盒顶、
     * 内部 round 到 0.1 像素并把 y 上移 1px；支持 {@code §} 颜色码与换行）。
     *
     * @param baseMatrix 调用方所在空间的基矩阵（一般即 {@code poseStack.last().pose()} 的副本）
     */
    public void appendText(Matrix4f baseMatrix, String text, float x, float y, int argb) {
        if (!batchOpen || text == null || text.isEmpty()) {
            return;
        }
        float a = (argb >>> 24 & 0xFF) / 255.0f;
        float r0 = (argb >> 16 & 0xFF) / 255.0f;
        float g0 = (argb >> 8 & 0xFF) / 255.0f;
        float b0 = (argb & 0xFF) / 255.0f;
        float curR = r0;
        float curG = g0;
        float curB = b0;
        batchScratch.set(baseMatrix)
                .translate((float) MathUtil.round(x, 1), (float) MathUtil.round(--y, 1), 0.0f)
                .scale(1.0f / scale, 1.0f / scale, 1.0f);
        Matrix4f matrix = batchScratch;
        float penX = 0.0f;
        float penY = 0.0f;
        boolean inFormatting = false;
        int lineStart = 0;
        synchronized (glyphPageMap) {
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (inFormatting) {
                    inFormatting = false;
                    char upper = Character.toUpperCase(ch);
                    if (MC_COLOR_CODES.containsKey(upper)) {
                        int[] rgb = colorToRGB(MC_COLOR_CODES.get(upper));
                        curR = rgb[0] / 255.0f;
                        curG = rgb[1] / 255.0f;
                        curB = rgb[2] / 255.0f;
                    } else if (upper == 'R') {
                        curR = r0;
                        curG = g0;
                        curB = b0;
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
                            GlyphPage page = glyph.owner();
                            if (batchPage == null || !batchPage.equals(page.textureLocation)) {
                                switchBatchPage(page.textureLocation);
                            }
                            emitGlyph(matrix, penX, penY, glyph, page, curR, curG, curB, a);
                        }
                        penX += glyph.width() + letterSpacing;
                    }
                }
            }
        }
    }

    /** 结束批次：一次 draw 提交全部字形。 */
    public void endTextBatch() {
        if (!batchOpen) {
            return;
        }
        batchOpen = false;
        // 批次期间若按需栅格化了新字形，先上传再画；flush 会重建纹理，故之后必须重新绑定
        if (flushDirtyPages() && batchPage != null) {
            RenderSystem.setShaderTexture(0, batchPage);
        }
        BufferUploader.drawWithShader(batchBuffer.end());
        RenderSystem.disableBlend();
        batchBuffer = null;
        batchPage = null;
    }

    /** 批次中途换图集页：先把已有内容提交，再换纹理、重开缓冲区。 */
    private void switchBatchPage(ResourceLocation page) {
        // 换页多半正是因为刚才按需加载了新字形 → 必须先 flush（会重建纹理）
        if (flushDirtyPages() && batchPage != null) {
            RenderSystem.setShaderTexture(0, batchPage);
        }
        BufferUploader.drawWithShader(batchBuffer.end());
        RenderSystem.setShaderTexture(0, page);
        batchPage = page;
        batchBuffer = Tesselator.getInstance().getBuilder();
        batchBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    /** 发射单个字形的四个顶点（与 {@link #drawStringRGB} 的四边形逐字一致）。 */
    private void emitGlyph(Matrix4f matrix, float atX, float atY, Glyph glyph, GlyphPage page,
                           float r, float g, float b, float a) {
        float glyphWidth = glyph.width();
        float glyphHeight = glyph.height();
        float u1 = (float) glyph.u() / page.imageWidth;
        float v1 = (float) glyph.v() / page.imageHeight;
        float u2 = (float) (glyph.u() + glyph.width()) / page.imageWidth;
        float v2 = (float) (glyph.v() + glyph.height()) / page.imageHeight;
        batchBuffer.vertex(matrix, atX, atY + glyphHeight, 0.0f).uv(u1, v2).color(r, g, b, a).endVertex();
        batchBuffer.vertex(matrix, atX + glyphWidth, atY + glyphHeight, 0.0f).uv(u2, v2).color(r, g, b, a).endVertex();
        batchBuffer.vertex(matrix, atX + glyphWidth, atY, 0.0f).uv(u2, v1).color(r, g, b, a).endVertex();
        batchBuffer.vertex(matrix, atX, atY, 0.0f).uv(u1, v1).color(r, g, b, a).endVertex();
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

    /**
     * 取某字符在已栅格化图谱中的真实墨迹边界（逻辑像素，相对笔位）。带缓存。
     * <p>
     * 与 {@link #getGlyphVisualBounds(char)} 的区别：本方法扫描 {@link GlyphPage} 实际绘制到
     * 图谱的像素，而非 AWT {@code GlyphVector} 的几何 bounds——quad 上屏后经 bilinear 下采样
     * 呈现给玩家的最终位置以图谱为准，因此用于「以中心点居中」的绘制（如图标 / 单字符徽标）
     * 时不会出现 1~2 像素的几何↔像素漂移。命中失败（字符未栅格化 / 空白）时回落到
     * {@link #getGlyphVisualBounds(char)} 的几何结果，保证可用。
     */
    public GlyphVisualBounds getGlyphRenderedBounds(char c) {
        synchronized (glyphRenderedCache) {
            GlyphVisualBounds cached = glyphRenderedCache.get(c);
            if (cached != null) {
                return cached;
            }
        }
        GlyphVisualBounds measured = measureGlyphRenderedBounds(c);
        synchronized (glyphRenderedCache) {
            GlyphVisualBounds existing = glyphRenderedCache.get(c);
            if (existing != null) {
                return existing;
            }
            glyphRenderedCache.put(c, measured);
        }
        return measured;
    }

    private GlyphVisualBounds measureGlyphRenderedBounds(char c) {
        Glyph glyph = getOrLoadGlyph(c);
        if (glyph != null) {
            BufferedImage atlas = glyph.owner().getAtlasImage();
            if (atlas != null) {
                int[] ink = glyph.owner().scanGlyphInkBounds(glyph.u(), glyph.v(), glyph.width(), glyph.height());
                if (ink != null) {
                    float k = 1.0f / scale;
                    // 笔位在 quad 内的位置：x = quad.u；y = quad.v + ascent
                    int ascent = fontMetrics.getAscent();
                    return new GlyphVisualBounds(
                            ink[0] * k,
                            (ink[1] - ascent) * k,
                            ink[2] * k,
                            ink[3] * k);
                }
            }
        }
        // 兜底：未栅格化或空白字符，回落到 AWT 几何 bounds
        return measureGlyphVisualBounds(c);
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
            // 实测墨迹（图谱像素扫描）同样依赖当前 scale：GUI 值 = 图谱像素 / scale，
            // guiScale 变化后必须一并失效，否则按墨迹做居中的调用方会拿到过期偏移。
            glyphRenderedCache.clear();
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
