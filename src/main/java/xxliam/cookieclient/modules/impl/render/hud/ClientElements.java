package xxliam.cookieclient.modules.impl.render.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.utils.game.MoveUtility;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client Elements：屏幕左/右下的客户端信息显示（XYZ / BPS / FPS + 药水状态）。
 * <p>
 * 复刻 opal {@code OverlayModule} 下的 {@code ClientElements} 元素（此前的 Xyz/Bps/Fps/
 * StatusEffects 四个独立模块按 opal 结构集成回一个模块，选项对应 opal
 * {@code ClientElementSettings}：XYZ 默认关，FPS/BPS/Status effects 默认开，Lowercase 默认开）。
 * <p>
 * 布局照搬 opal：左下 x=2 自底向上紧凑堆叠（一行 FONT_HEIGHT，只占开启项，关闭后下方项
 * 自动上移——opal 的动态 y 语义）；右下右对齐药水状态（文本宽度降序、行距 FONT_HEIGHT+0.5、
 * 效果色文字、时长 §7 灰、18x18 原版图标、文字右缘/图标间距沿用既有移植坐标）。
 */
public class ClientElements extends Module {

    /**
     * 行距基准对应 opal FONT_SIZE 8 / REGULAR_FONT 高度。
     * 注意：不能在类初始化（static/实例字段）时引用 FontStore —— ModuleManager 在
     * {@code onInitialize}（Minecraft 尚未创建，{@code mc.getWindow()==null}）里 new 本模块，
     * 会提前触发 FontStore 字体加载而崩溃；必须在渲染期（Minecraft 已就绪）取值。
     */

    public final BooleanSetting statusEffects = new BooleanSetting("Status effects", true);
    public final BooleanSetting fps = new BooleanSetting("FPS", true);
    public final BooleanSetting bps = new BooleanSetting("BPS", true);
    public final BooleanSetting xyz = new BooleanSetting("XYZ", false);
    public final BooleanSetting lowercase = new BooleanSetting("Lowercase", true);

    public ClientElements() {
        super("Client Elements", Category.RENDER);
        setVisible(false); // HUD 基础设施，不进 ModuleList 列表
        addSetting(statusEffects);
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

        // ===== 右下：药水状态 =====
        if (statusEffects.getValue()) {
            renderStatusEffects(guiGraphics, mc, fontHeight);
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

    // ==================== 右下药水状态（opal Status effects） ====================

    private void renderStatusEffects(GuiGraphics guiGraphics, Minecraft mc, float fontHeight) {
        Map<MobEffect, MobEffectInstance> effects = mc.player.getActiveEffectsMap();
        if (effects.isEmpty()) {
            return;
        }

        List<Map.Entry<MobEffect, MobEffectInstance>> list = new ArrayList<>(effects.entrySet());
        list.sort(Comparator.comparingDouble(e -> -getTextWidth(getStatusEffectString(e.getValue()))));

        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        float y = mc.getWindow().getGuiScaledHeight() - fontHeight - 3.0f;

        for (Map.Entry<MobEffect, MobEffectInstance> entry : list) {
            MobEffect effect = entry.getKey();
            MobEffectInstance instance = entry.getValue();

            String text = getStatusEffectString(instance);
            int textWidth = (int) FontStore.PRODUCTSANS_REGULAR_8.getStringWidth(text);
            int effectColor = effect.getColor() | 0xFF000000;

            FontStore.PRODUCTSANS_REGULAR_8.drawStringWithShadow(guiGraphics.pose(),
                    text, scaledWidth - textWidth - 1.0f, y, effectColor);

            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect);
            if (sprite != null) {
                float iconX = scaledWidth - textWidth - 12.0f;
                float iconY = y - 7.0f;
                // 原版药水图标 18x18（含 9x9 有效区域），按原版 Gui 渲染方式绘制
                RenderHelperBlit.blitSprite(guiGraphics, sprite, (int) iconX, (int) iconY, 18, 18);
            }

            y -= fontHeight + 0.5f;
        }
    }

    private float getTextWidth(String text) {
        return FontStore.PRODUCTSANS_REGULAR_8.getStringWidth(text);
    }

    private String getStatusEffectString(MobEffectInstance instance) {
        String name = Component.translatable(instance.getEffect().getDescriptionId()).getString();
        if (lowercase.getValue()) {
            name = name.toLowerCase(Locale.ROOT);
        }
        String duration = instance.isInfiniteDuration()
                ? "**:**"
                : formatTicks(instance.getDuration());
        return name
                + (instance.getAmplifier() > 0 ? " " + (instance.getAmplifier() + 1) : "")
                + " \u00a77" + duration;
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = (int) Math.floor((float) ticks / 20.0f);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
