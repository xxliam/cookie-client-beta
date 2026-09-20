package xxliam.cookieclient.modules.impl.render;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.ColorSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.utils.render.ClientTheme;
import xxliam.cookieclient.utils.render.ColorUtil;

/**
 * Theme：全局 HUD 主题模块（对应 opal Overlay 的 ClientTheme 主题选择）。
 * <p>
 * 两个维度：
 * <ul>
 *   <li><b>Color</b>（原参数名 {@code Theme}，为避免与新参数重名而更名）—— 配色主题，
 *       其它 HUD 元素通过 {@link #getColors()} 读取 (主色, 副色) 绘制渐变与强调色；
 *       选 CUSTOM 时显示两个自定义颜色设置。</li>
 *   <li><b>Theme</b>（DARK / LIGHT）—— GUI 明暗：DARK 为现有观感；LIGHT 把 ClickGUI 面板、
 *       ModuleList 行、灵动岛（Watermark）背景转为白色，文字/控件底随之反相。见
 *       {@link xxliam.cookieclient.utils.render.ThemeHelper} 的调色板。</li>
 * </ul>
 */
public class Theme extends Module {

    public static final String STYLE_DARK = "Dark";
    public static final String STYLE_LIGHT = "Light";

    public static Theme INSTANCE;

    /** 配色主题（主/副色来源）。 */
    private final ModeSetting colorMode;
    /** GUI 明暗（Dark = 现有观感 / Light = 背景转白）。 */
    private final ModeSetting guiTheme;
    private final ColorSetting primaryColor;
    private final ColorSetting secondaryColor;

    public Theme() {
        super("Theme", Category.RENDER);
        setVisible(false); // 主题仅影响 HUD 配色，不进 ModuleList 列表
        colorMode = new ModeSetting("Color", ClientTheme.names()).withDefault("Opal");
        guiTheme = new ModeSetting("Theme", STYLE_DARK, STYLE_LIGHT).withDefault(STYLE_DARK);
        primaryColor = new ColorSetting("Color 1", 0xFF2DBFFE, () -> colorMode.is("Custom"));
        secondaryColor = new ColorSetting("Color 2", 0xFF2499CB, () -> colorMode.is("Custom"));
        addSetting(colorMode);
        addSetting(guiTheme);
        addSetting(primaryColor);
        addSetting(secondaryColor);
        INSTANCE = this;
    }

    public ClientTheme getTheme() {
        return ClientTheme.fromName(colorMode.getValue());
    }

    /** 是否 Light 明暗主题（背景转白那档）。 */
    public boolean isLight() {
        return guiTheme.is(STYLE_LIGHT);
    }

    /** 返回 [主色, 副色] ARGB。CUSTOM 用模块设置；RAINBOW 动态循环；其余取预设。 */
    public int[] getColors() {
        ClientTheme theme = getTheme();
        if (theme == ClientTheme.CUSTOM) {
            return new int[]{primaryColor.getColor(), secondaryColor.getColor()};
        }
        if (theme == ClientTheme.RAINBOW) {
            long t = System.currentTimeMillis() / 20L;
            int primary = ColorUtil.rainbow(20, (int) (t % 360), 1, 1);
            int secondary = ColorUtil.rainbow(20, (int) ((t + 40) % 360), 1, 1);
            return new int[]{primary, secondary};
        }
        return theme.getStaticColors();
    }
}
