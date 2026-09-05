package xxliam.cookieclient.utils.render;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.impl.render.Theme;

/**
 * 主题辅助：从 Theme 模块读取当前主题 (主色, 副色)。
 * <p>
 * Theme 模块未注册/未构造时回退 opal 默认主题。
 */
public final class ThemeHelper {

    private ThemeHelper() {
    }

    /** 返回 [主色, 副色] ARGB。 */
    public static int[] getThemeColors() {
        Theme theme = Theme.INSTANCE;
        if (theme == null && CookieClient.MODULE_MANAGER != null) {
            Object module = CookieClient.MODULE_MANAGER.getModule("Theme");
            if (module instanceof Theme t) {
                theme = t;
            }
        }
        if (theme != null) {
            return theme.getColors();
        }
        return new int[]{0xFF2DBFFE, 0xFF2499CB}; // opal 默认 Opal 主题
    }
}
