package xxliam.cookieclient.utils.render;

import java.awt.Color;

/**
 * 客户端主题预设（照搬 OpenOpal 的 {@code ClientTheme}，颜色值逐项一致）。
 * <p>
 * CUSTOM / RAINBOW 的颜色在渲染时由 {@link xxliam.cookieclient.modules.impl.render.Theme} 模块动态提供。
 */
public enum ClientTheme {

    OPAL("Opal", new Color(45, 191, 254), new Color(36, 153, 203)),
    SPEARMINT("Spearmint", new Color(97, 194, 162), new Color(65, 130, 108)),
    JADE_GREEN("Jade Green", new Color(0, 168, 107), new Color(0, 105, 66)),
    GREEN_SPIRIT("Green Spirit", new Color(159, 226, 191), new Color(0, 135, 62)),
    ROSY_PINK("Rosy Pink", new Color(255, 102, 204), new Color(191, 77, 153)),
    MAGENTA("Magenta", new Color(213, 63, 119), new Color(157, 68, 110)),
    HOT_PINK("Hot Pink", new Color(231, 84, 128), new Color(172, 79, 198)),
    LAVENDER("Lavender", new Color(219, 166, 247), new Color(152, 115, 172)),
    AMETHYST("Amethyst", new Color(144, 99, 205), new Color(98, 67, 140)),
    PURPLE_FIRE("Purple Fire", new Color(177, 162, 202), new Color(104, 71, 141)),
    SUNSET_PINK("Sunset Pink", new Color(255, 145, 20), new Color(245, 105, 231)),
    BLAZE_ORANGE("Blaze Orange", new Color(255, 169, 77), new Color(255, 130, 0)),
    PINK_BLOOD("Pink Blood", new Color(255, 166, 201), new Color(228, 0, 70)),
    PASTEL("Pastel", new Color(255, 109, 106), new Color(191, 82, 80)),
    NEON_RED("Neon Red", new Color(210, 39, 48), new Color(184, 25, 42)),
    RED_COFFEE("Red Coffee", new Color(225, 34, 59), new Color(75, 19, 19)),
    DEEP_OCEAN("Deep Ocean", new Color(60, 82, 145), new Color(0, 20, 64)),
    CHAMBRAY_BLUE("Chambray Blue", new Color(60, 82, 145), new Color(33, 46, 182)),
    MINT_BLUE("Mint Blue", new Color(66, 158, 157), new Color(40, 94, 93)),
    PACIFIC_BLUE("Pacific Blue", new Color(5, 169, 199), new Color(4, 115, 135)),
    TROPICAL_ICE("Tropical Ice", new Color(102, 255, 209), new Color(6, 149, 255)),
    BLUE_PURPLE("Blue Purple", new Color(104, 77, 178), new Color(4, 60, 174)),
    RAINBOW("Rainbow", Color.BLACK, Color.BLACK),
    CUSTOM("Custom", Color.BLACK, Color.BLACK);

    private final String name;
    private final Color color;
    private final Color alternateColor;

    ClientTheme(final String name, final Color color, final Color alternateColor) {
        this.name = name;
        this.color = color;
        this.alternateColor = alternateColor;
    }

    @Override
    public String toString() {
        return name;
    }

    /** 返回 (主色, 副色)，ARGB int 对。CUSTOM/RAINBOW 返回黑，由调用方走 Theme 模块取动态色。 */
    public int[] getStaticColors() {
        return new int[]{color.getRGB(), alternateColor.getRGB()};
    }

    public static String[] names() {
        ClientTheme[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name;
        }
        return names;
    }

    public static ClientTheme fromName(String name) {
        for (ClientTheme theme : values()) {
            if (theme.name.equalsIgnoreCase(name)) {
                return theme;
            }
        }
        return OPAL;
    }
}
