package xxliam.cookieclient.modules.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.hud.DynamicIsland;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * Watermark：水印系统总模块（Render 分类），渲染顶栏居中的 {@link DynamicIsland} 灵动岛。
 * <p>
 * 原移植自 OpenZen {@code shit.zen.modules.impl.render.Watermark} 的两种 Style
 * （Neverlose 顶栏胶囊 / DynamicIsland 灵动岛）已按需求<b>移除 Neverlose</b>，只保留灵动岛。
 * 由 {@code GuiMixin} 在游戏内逐帧驱动 {@link #render(GuiGraphics, float)}。
 */
public class Watermark extends Module {

    public static Watermark INSTANCE;

    /** 灵动岛背景不透明度（10%~100%，默认 16% ≈ zen 原 (0,0,0,40) 的观感）。 */
    private final NumberSetting backgroundOpacity =
            new NumberSetting("Background Opacity", 16.0, 10.0, 100.0, 1.0);

    /**
     * 灵动岛上下拖动偏移（布局编辑态写入；不可见设置项，仅用于持久化，与 ModuleList 的
     * X/Y offset 同款做法）。
     */
    private final NumberSetting islandOffsetY =
            new NumberSetting("Y offset", 0.0d, -10000.0d, 10000.0d, 1.0d, () -> false);

    private final DynamicIsland dynamicIsland = new DynamicIsland(islandOffsetY);

    public Watermark() {
        super("Watermark", Category.RENDER);
        INSTANCE = this;
        addSetting(backgroundOpacity);
        addSetting(islandOffsetY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }
        dynamicIsland.render(guiGraphics, backgroundOpacity.getValue().floatValue());
    }
}
