package xxliam.cookieclient.gui.panelclickgui.support;

import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panel 风格 ClickGUI 的字体工厂，对应 OpenZen 的 {@code shit.zen.render.FontPresets}。
 * <p>
 * <b>字号语义与 zen 完全一致</b>：传入的 {@code size} 是 zen 的原始字号，实际渲染字号 = {@code size / 2}
 * （zen 的 {@code Fonts.getCustomFont} 与 cookie 的 {@link FontStore#loadFont(float, String)} 都是
 * {@code deriveFont(0, size / 2.0f)} + {@code new CustomFont(font, size / 2.0f)}，链路等价）。
 * 因此移植时 {@code FontPresets.axiformaRegular(14.0f * scale)} 可逐字改成
 * {@link #axiformaRegular(float)}({@code 14.0f * scale})。
 * <p>
 * 按「文件名 + 字号」缓存（与 zen 的 {@code fontRendererCache} 同策略）：Panel 的 scale 是有限档位，
 * 不会无限膨胀。
 */
public final class PanelFonts {

    private static final Map<String, CustomFont> POOL = new ConcurrentHashMap<>();

    private PanelFonts() {
    }

    public static CustomFont axiformaRegular(float size) {
        return of("axiforma_regular.ttf", size);
    }

    public static CustomFont axiformaBold(float size) {
        return of("axiforma_bold.ttf", size);
    }

    public static CustomFont axiformaExtraBold(float size) {
        return of("axiforma_extrabold.ttf", size);
    }

    /** Material 图标字形（分类图标、箭头、勾选等）。 */
    public static CustomFont materialIcons(float size) {
        return of("materialicons-regular.ttf", size);
    }

    /** Museo Sans 900（Panel 里的强调字）。 */
    public static CustomFont museoSans(float size) {
        return of("MuseoSansCyrl-900.ttf", size);
    }

    private static CustomFont of(String file, float size) {
        return POOL.computeIfAbsent(file + '@' + size, key -> FontStore.loadFont(size, file));
    }
}
