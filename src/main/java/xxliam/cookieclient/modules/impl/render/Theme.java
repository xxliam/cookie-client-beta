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
 * 其它 HUD 元素通过 {@link #getColors()} 读取当前主题的 (主色, 副色) 绘制渐变与强调色。
 * 选择 CUSTOM 时显示两个自定义颜色设置。
 */
public class Theme extends Module {

    public static Theme INSTANCE;

    private final ModeSetting themeMode;
    private final ColorSetting primaryColor;
    private final ColorSetting secondaryColor;

    public Theme() {
        super("Theme", Category.RENDER);
        setVisible(false); // 主题仅影响 HUD 配色，不进 ModuleList 列表
        themeMode = new ModeSetting("Theme", ClientTheme.names()).withDefault("Opal");
        primaryColor = new ColorSetting("Color 1", 0xFF2DBFFE, () -> themeMode.is("Custom"));
        secondaryColor = new ColorSetting("Color 2", 0xFF2499CB, () -> themeMode.is("Custom"));
        addSetting(themeMode);
        addSetting(primaryColor);
        addSetting(secondaryColor);
        INSTANCE = this;
    }

    public ClientTheme getTheme() {
        return ClientTheme.fromName(themeMode.getValue());
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
