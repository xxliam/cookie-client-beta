package xxliam.cookieclient.modules.impl.render.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.utils.game.MoveUtility;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.Locale;

/**
 * Client Elements：屏幕左下的客户端信息显示（XYZ / BPS / FPS）。
 * <p>
 * 复刻 opal {@code OverlayModule} 下的 {@code ClientElements} 元素（此前的 Xyz/Bps/Fps
 * 三个独立模块按 opal 结构集成回一个模块，选项对应 opal
 * {@code ClientElementSettings}：XYZ 默认关，FPS/BPS 默认开，Lowercase 默认开）。
 * <p>
 * 布局照搬 opal：左下 x=2 自底向上紧凑堆叠（一行 FONT_HEIGHT，只占开启项，关闭后下方项
 * 自动上移——opal 的动态 y 语义）。右下药水状态（opal Status effects）已按需求删除。
 */
public class ClientElements extends Module {

    public final BooleanSetting fps = new BooleanSetting("FPS", true);
    public final BooleanSetting bps = new BooleanSetting("BPS", true);
    public final BooleanSetting xyz = new BooleanSetting("XYZ", false);
    public final BooleanSetting lowercase = new BooleanSetting("Lowercase", true);

    public ClientElements() {
        super("Client Elements", Category.RENDER);
        setVisible(false); // HUD 基础设施，不进 ModuleList 列表
        addSetting(fps);
        addSetting(bps);
        addSetting(xyz);
        addSetting(lowercase);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }

        float scaledHeight = mc.getWindow().getGuiScaledHeight();
        int[] colors = ThemeHelper.getThemeColors();
        float fontHeight = FontStore.PRODUCTSANS_REGULAR_8.getFontHeight();

        // ===== 左下：XYZ / BPS / FPS 动态堆叠 =====
        float y = scaledHeight - fontHeight - 3.0f; // 最底行字底距屏幕底 3px
        if (xyz.getValue()) {
            drawTextInfo(guiGraphics, colors, "XYZ", getXyzText(mc), y);
            y -= fontHeight;
        }
        if (bps.getValue()) {
            drawTextInfo(guiGraphics, colors, "BPS", getBpsText(), y);
            y -= fontHeight;
        }
        if (fps.getValue()) {
            drawTextInfo(guiGraphics, colors, "FPS", getFpsText(mc), y);
        }
    }

    /** 一行「主题渐变前缀 + 白色值」（opal drawGradientStringWithShadow + 值）。 */
    private void drawTextInfo(GuiGraphics guiGraphics, int[] colors, String prefix, String value, float y) {
        if (lowercase.getValue()) {
            prefix = prefix.toLowerCase(Locale.ROOT);
        }
        float x = 2.0f;
        FontStore.PRODUCTSANS_BOLD_8.drawGradientStringWithShadow(guiGraphics.pose(), prefix, x, y, colors[0], colors[1]);
        float prefixWidth = FontStore.PRODUCTSANS_BOLD_8.getStringWidth(prefix + " ");
        FontStore.PRODUCTSANS_REGULAR_8.drawStringWithShadow(guiGraphics.pose(), value, x + prefixWidth + 2.0f, y, 0xFFFFFFFF);
    }

    private String getXyzText(Minecraft mc) {
        Vec3 pos = mc.player.position();
        return String.format(Locale.ROOT, "%.0f %.0f %.0f", pos.x, pos.y, pos.z);
    }

    private String getBpsText() {
        return String.format(Locale.ROOT, "%.2f", MoveUtility.getBlocksPerSecond());
    }

    private String getFpsText(Minecraft mc) {
        return String.valueOf(mc.getFps());
    }
}
