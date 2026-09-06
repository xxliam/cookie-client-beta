package xxliam.cookieclient.gui.dropdownclickgui.panel.property;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.Component;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownRender;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.utils.render.ColorUtil;

/**
 * 属性行基类（Opal {@code PropertyPanel}）：默认行高 17、行底色半透明黑 25%，
 * 最后一个可见属性（且模块行在列表末）底部两角圆角 5。
 */
public abstract class PropertyPanel extends Component {

    protected static final float DEFAULT_HEIGHT = 17.0f;

    protected final Setting<?> setting;
    protected boolean lastProperty;

    public PropertyPanel(final Setting<?> setting) {
        this.setting = setting;
        setHeight(DEFAULT_HEIGHT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        int bg = ColorUtil.applyOpacity(0xFF000000, 0.25f * alpha);
        if (lastProperty) {
            Renderer.drawRoundedRect(guiGraphics.pose(), x, y, width, height, 0.0f, 0.0f, 5.0f, 5.0f, bg);
        } else {
            Renderer.drawRect(guiGraphics.pose(), x, y, width, height, bg);
        }
    }

    public boolean isHidden() {
        return !setting.getVisibility().displayable();
    }

    public Setting<?> getSetting() {
        return setting;
    }

    /** 基线文本（medium 7 视觉常用）。 */
    protected void label(GuiGraphics g, String text, float lx, float baselineY, float alpha) {
        DropdownRender.baseline(g, FontStore.PRODUCTSANS_MEDIUM_7, text, lx, baselineY,
                ColorUtil.withAlpha(-1, alpha));
    }

    protected void baseline(GuiGraphics g, CustomFont font, String text, float lx, float baselineY, int color) {
        DropdownRender.baseline(g, font, text, lx, baselineY, color);
    }

    protected static boolean isHovering(float x, float y, float w, float h, double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
