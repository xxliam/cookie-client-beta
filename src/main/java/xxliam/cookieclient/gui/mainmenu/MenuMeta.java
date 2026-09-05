package xxliam.cookieclient.gui.mainmenu;

import net.fabricmc.loader.api.FabricLoader;
import xxliam.cookieclient.CookieClient;

/** 主菜单品牌元数据（名称/版本）。 */
public final class MenuMeta {

    private MenuMeta() {
    }

    public static String displayName() {
        return CookieClient.NAME;
    }

    public static String version() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer(CookieClient.MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("1.0.0");
        } catch (Throwable ignored) {
            return "1.0.0";
        }
    }

    public static String cleanVersion() {
        return version().replaceFirst("^[vV]", "");
    }
}
