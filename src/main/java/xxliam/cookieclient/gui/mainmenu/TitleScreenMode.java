package xxliam.cookieclient.gui.mainmenu;

/** 会话级选择：cookie 主菜单 或 原版标题屏。 */
public final class TitleScreenMode {

    private static boolean vanilla;

    private TitleScreenMode() {
    }

    public static void useVanilla() {
        vanilla = true;
    }

    public static void useCookie() {
        vanilla = false;
    }

    public static boolean isVanilla() {
        return vanilla;
    }
}
