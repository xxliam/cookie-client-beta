package xxliam.cookieclient.gui.dropdownclickgui.panel.property;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.Component;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl.BooleanSettingPanel;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl.ColorSettingPanel;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl.ModeSettingPanel;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl.MultiSelectSettingPanel;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl.NumberSettingPanel;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ColorSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.MultiSelectSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 模块属性容器：把 cookie 的 {@link Setting} 列表按类型分派为 dropdown 风格的面板行。
 * <p>
 * 移植自 OpenOpal {@code wtf.opal.client.screen.click.dropdown.panel.property.PropertyProvider}。
 * Opal 的 Property 体系比 cookie 的 Setting 多出 Group / BoundedNumber / String / ScreenPosition
 * 四种类型（cookie 模块未使用 → 无对应分派，不创建行）。
 */
public class PropertyProvider extends Component {

    private final List<PropertyPanel> panels = new ArrayList<>();
    private final Module module;
    private final BooleanSupplier expanded;
    private final BooleanSupplier lastModule;

    public PropertyProvider(final Module module, final BooleanSupplier expanded, final BooleanSupplier lastModule) {
        this.module = module;
        this.expanded = expanded;
        this.lastModule = lastModule;
        initProperties();
    }

    public PropertyProvider(final Module module, final BooleanSupplier expanded) {
        this(module, expanded, () -> false);
    }

    private void initProperties() {
        for (Setting<?> setting : module.getSettings()) {
            PropertyPanel panel = create(setting, module);
            if (panel != null) {
                panels.add(panel);
            }
        }
    }

    /** cookie Setting → dropdown 行（opal Property#createClickGUIComponent 的等价物）。 */
    private static PropertyPanel create(Setting<?> setting, Module owner) {
        if (setting instanceof BooleanSetting s) {
            return new BooleanSettingPanel(s);
        }
        if (setting instanceof ModeSetting s) {
            return new ModeSettingPanel(s, owner);
        }
        if (setting instanceof MultiSelectSetting s) {
            return new MultiSelectSettingPanel(s);
        }
        if (setting instanceof NumberSetting s) {
            return new NumberSettingPanel(s);
        }
        if (setting instanceof ColorSetting s) {
            return new ColorSettingPanel(s);
        }
        return null; // cookie 无 Group/Bounded/String/ScreenPosition 设置类型
    }

    private boolean isClosed() {
        return !expanded.getAsBoolean();
    }

    private float extraHeight;

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        if (isClosed()) {
            extraHeight = 0.0f;
            return;
        }

        float currentExtra = 0.0f;
        int lastVisibleIndex = -1;
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (!panels.get(i).isHidden()) {
                lastVisibleIndex = i;
                break;
            }
        }

        for (int i = 0; i < panels.size(); i++) {
            PropertyPanel panel = panels.get(i);
            if (panel.isHidden()) {
                continue;
            }
            panel.setX(x);
            panel.setY(y + currentExtra);
            panel.setWidth(width);
            panel.lastProperty = lastModule.getAsBoolean() && i == lastVisibleIndex;
            panel.render(guiGraphics, mouseX, mouseY, delta, alpha);
            currentExtra += panel.getHeight();
        }

        this.extraHeight = currentExtra;
    }

    public float getExtraHeight() {
        return extraHeight;
    }

    public boolean isHasProperties() {
        for (PropertyPanel panel : panels) {
            if (!panel.isHidden()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void init() {
        for (PropertyPanel panel : panels) {
            panel.init();
        }
    }

    @Override
    public void close() {
        for (PropertyPanel panel : panels) {
            panel.close();
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isClosed()) {
            return;
        }
        for (PropertyPanel panel : panels) {
            if (!panel.isHidden()) {
                panel.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isClosed()) {
            return;
        }
        for (PropertyPanel panel : panels) {
            if (!panel.isHidden()) {
                panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (isClosed()) {
            return;
        }
        for (PropertyPanel panel : panels) {
            if (!panel.isHidden()) {
                panel.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void keyPressed(int keyCode) {
        if (isClosed()) {
            return;
        }
        for (PropertyPanel panel : panels) {
            panel.keyPressed(keyCode);
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (isClosed()) {
            return;
        }
        for (PropertyPanel panel : panels) {
            panel.charTyped(chr, modifiers);
        }
    }
}
