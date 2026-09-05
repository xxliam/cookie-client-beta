package xxliam.cookieclient.gui.mainmenu;

import xxliam.cookieclient.render.CustomFont;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Setsuna 主菜单字体仓库：pf_normal / pf_middleblack（照搬 Setsuna tritium 字体资源）。
 * <p>
 * 懒加载（按需首次使用才建字体对象），规避 static 初始化阶段 Minecraft/Window 尚未就绪的崩溃；
 * 每个视觉字号一个 {@link CustomFont}，字形按需入图集。
 */
public final class MenuFonts {

    private static final String REGULAR_FILE = "/assets/cookie-client/fonts/pf_normal.ttf";
    private static final String BOLD_FILE = "/assets/cookie-client/fonts/pf_middleblack.ttf";
    private static final String[] SYMBOL_FAMILIES = {
            "Segoe UI Symbol", "Segoe UI", "Arial Unicode MS", "Microsoft YaHei UI"
    };

    private static final Map<Integer, CustomFont> REGULAR_FONTS = new HashMap<>();
    private static final Map<Integer, CustomFont> BOLD_FONTS = new HashMap<>();
    private static final Map<Integer, CustomFont> SYMBOL_FONTS = new HashMap<>();

    private static Font regularBase;
    private static Font boldBase;
    private static Font symbolBase;

    private MenuFonts() {
    }

    /** Setsuna Skia 字号 S（GUI 单位）对应的 regular 字体。 */
    public static synchronized CustomFont regular(float size) {
        int key = sizeKey(size);
        return REGULAR_FONTS.computeIfAbsent(key, ignored -> create(REGULAR_FILE, false, size));
    }

    /** Setsuna Skia 字号 S（GUI 单位）对应的 bold 字体。 */
    public static synchronized CustomFont bold(float size) {
        int key = sizeKey(size);
        return BOLD_FONTS.computeIfAbsent(key, ignored -> create(BOLD_FILE, true, size));
    }

    /** 系统符号字体（pf 不覆盖 ⇄ 等 UI 图形字符时用于字形兜底）。 */
    public static synchronized CustomFont symbol(float size) {
        int key = sizeKey(size);
        return SYMBOL_FONTS.computeIfAbsent(key, ignored -> {
            java.awt.Font face = symbolFont();
            float safe = Math.max(1.0F, size);
            return new CustomFont(face, safe, 256, 0, null);
        });
    }

    public static boolean canDisplaySymbol(int codePoint) {
        return symbolFont().canDisplay(codePoint);
    }

    private static int sizeKey(float size) {
        return Math.max(1, Math.round(size * 10.0F));
    }

    private static CustomFont create(String file, boolean boldFace, float size) {
        java.awt.Font face = baseFont(file, boldFace);
        float safe = Math.max(1.0F, size);
        return new CustomFont(face, safe, 256, 0, null);
    }

    private static synchronized java.awt.Font baseFont(String file, boolean boldFace) {
        if (boldFace && boldBase != null) return boldBase;
        if (!boldFace && regularBase != null) return regularBase;
        try (InputStream stream = MenuFonts.class.getResourceAsStream(file)) {
            if (stream == null) {
                throw new java.io.IOException("Menu font not found: " + file);
            }
            java.awt.Font created = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(Font.PLAIN, 24.0F);
            if (boldFace) boldBase = created; else regularBase = created;
            return created;
        } catch (Exception exception) {
            // 字体损坏时退回系统 sans（仅兜底，正常资源不应走到）
            java.awt.Font fallback = new Font(Font.SANS_SERIF, boldFace ? Font.BOLD : Font.PLAIN, 24);
            if (boldFace) boldBase = fallback; else regularBase = fallback;
            return fallback;
        }
    }

    /** 优先找能显示 ⇄ 的系统字体族，最后退回逻辑 SansSerif。 */
    private static synchronized java.awt.Font symbolFont() {
        if (symbolBase != null) {
            return symbolBase;
        }
        java.awt.Font chosen = null;
        for (String family : SYMBOL_FAMILIES) {
            java.awt.Font candidate = new Font(family, Font.PLAIN, 24);
            if (candidate.getFamily().equalsIgnoreCase(family) && candidate.canDisplay(0x21C4)) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) {
            chosen = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
        }
        symbolBase = chosen;
        return chosen;
    }
}
