package xxliam.cookieclient.modules;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.settings.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能模块基类。所有功能（战斗 / 移动 / 渲染等）都继承此类。
 * <p>
 * 使用方式：
 * <pre>
 * public class Sprint extends Module {
 *     public Sprint() {
 *         super("Sprint", Category.MOVEMENT);
 *     }
 * }
 * </pre>
 */
public abstract class Module {

    private final String name;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private int keyBind;
    private boolean enabled;
    private boolean visible = true;

    protected Module(String name, Category category) {
        this(name, category, 0);
    }

    protected Module(String name, Category category, int keyBind) {
        this.name = name;
        this.category = category;
        this.keyBind = keyBind;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 是否显示在模块列表 HUD 中（默认可见）。 */
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * 该模块的开关状态是否写入 modules.json 持久化并在启动时恢复。
     * <p>
     * 默认 true。GUI 类模块（如 ClickGui）的 enabled 只在界面打开期间有意义，
     * 一旦随配置保存并在下次启动时恢复，会触发 setScreen 弹窗甚至启动期崩溃，
     * 应返回 false 完全排除出持久化。
     */
    public boolean shouldPersistEnabled() {
        return true;
    }

    /** 模块列表右侧附加的实时状态后缀（如开火速率），无则 null。 */
    public String getSuffix() {
        return null;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    protected void addSetting(Setting<?> setting) {
        settings.add(setting);
    }

    /** 设置启用状态，触发 onEnable / onDisable 回调。 */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
        xxliam.cookieclient.modules.impl.render.hud.Notifications.onModuleToggled(this, enabled);
    }

    public void enable() {
        setEnabled(true);
    }

    public void disable() {
        setEnabled(false);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    // ---- 生命周期回调（子类覆写） ----

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /**
     * 每游戏刻调用（由 ClientTickMixin 驱动）。
     */
    public void onTick() {
    }

    /**
     * 每帧调用（由 GuiMixin 驱动 HUD 渲染），仅启用时调用。
     *
     * @param guiGraphics  渲染上下文（1.20.1 由 {@code Gui.render} 传入）
     * @param partialTicks 帧间插值
     */
    public void render(GuiGraphics guiGraphics, float partialTicks) {
    }
}
