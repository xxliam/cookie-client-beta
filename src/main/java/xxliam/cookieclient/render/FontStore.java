package xxliam.cookieclient.render;

import java.awt.Font;
import java.io.InputStream;

/**
 * 字体仓库：集中加载 ClickGUI 使用的自定义字体。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.render.FontStore}，资源命名空间改为 cookie-client。
 */
public final class FontStore {

    public static final CustomFont MATERIAL_20 = loadFont(20.0f, "material.ttf");
    public static final CustomFont MATERIAL_14 = loadFont(14.0f, "material.ttf");
    public static final CustomFont AXIFORMA_REGULAR_14 = loadFont(14.0f, "axiforma_regular.ttf");
    public static final CustomFont AXIFORMA_REGULAR_16 = loadFont(16.0f, "axiforma_regular.ttf");
    public static final CustomFont AXIFORMA_BOLD_13 = loadFont(13.0f, "axiforma_bold.ttf");
    public static final CustomFont AXIFORMA_BOLD_16 = loadFont(16.0f, "axiforma_bold.ttf");
    public static final CustomFont AXIFORMA_BOLD_18 = loadFont(18.0f, "axiforma_bold.ttf");
    public static final CustomFont AXIFORMA_EXTRABOLD_16 = loadFont(16.0f, "axiforma_extrabold.ttf");
    public static final CustomFont AXIFORMA_EXTRABOLD_18 = loadFont(18.0f, "axiforma_extrabold.ttf");

    // ---- opal hud 字体（照搬 OpenOpal 视觉参数；loadFont 渲染字高≈入参/2，故入参=视觉字号×2） ----
    public static final CustomFont PRODUCTSANS_REGULAR_8 = loadFont(16.0f, "productsans-regular.ttf");
    public static final CustomFont PRODUCTSANS_REGULAR_10 = loadFont(20.0f, "productsans-regular.ttf");
    public static final CustomFont PRODUCTSANS_MEDIUM_8 = loadFont(16.0f, "productsans-medium.ttf");
    public static final CustomFont PRODUCTSANS_BOLD_8 = loadFont(16.0f, "productsans-bold.ttf");
    public static final CustomFont PRODUCTSANS_BOLD_12 = loadFont(24.0f, "productsans-bold.ttf");
    public static final CustomFont MATERIALICONS_14 = loadFont(28.0f, "materialicons-regular.ttf");

    // ---- ESP 名牌字号（照搬 opal ESPModule：productsans-bold 5px + materialicons-regular 5px） ----
    public static final CustomFont PRODUCTSANS_BOLD_5 = loadFont(10.0f, "productsans-bold.ttf");
    public static final CustomFont MATERIALICONS_5 = loadFont(10.0f, "materialicons-regular.ttf");

    // ---- 通知卡字号（视觉 7 / 6.5 / 7.5，照搬 opal NotificationsElement 的 7 / 6.5 / 7.5） ----
    public static final CustomFont PRODUCTSANS_BOLD_7 = loadFont(14.0f, "productsans-bold.ttf");
    public static final CustomFont PRODUCTSANS_MEDIUM_6_5 = loadFont(13.0f, "productsans-medium.ttf");
    public static final CustomFont PRODUCTSANS_MEDIUM_7_5 = loadFont(15.0f, "productsans-medium.ttf");

    private FontStore() {
    }

    public static CustomFont loadFont(float size, String name) {
        try (InputStream stream = FontStore.class.getResourceAsStream("/assets/cookie-client/fonts/" + name)) {
            if (stream == null) {
                throw new java.io.IOException("Font not found: " + name);
            }
            return new CustomFont(Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(0, size / 2.0f), size / 2.0f);
        } catch (Exception e) {
            return new CustomFont(new Font("SansSerif", Font.PLAIN, (int) (size / 2)), size / 2.0f);
        }
    }
}
