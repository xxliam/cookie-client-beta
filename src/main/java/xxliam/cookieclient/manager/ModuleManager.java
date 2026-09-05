package xxliam.cookieclient.manager;

import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.exception.ModuleNotFoundException;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.modules.impl.combat.AntiKB;
import xxliam.cookieclient.modules.impl.combat.AutoClicker;
import xxliam.cookieclient.modules.impl.combat.KillAura;
import xxliam.cookieclient.modules.impl.exploit.Disabler;
import xxliam.cookieclient.modules.impl.misc.AimAssist;
import xxliam.cookieclient.modules.impl.movement.GuiMove;
import xxliam.cookieclient.modules.impl.movement.NoDelay;
import xxliam.cookieclient.modules.impl.movement.Scaffold;
import xxliam.cookieclient.modules.impl.movement.Sprint;
import xxliam.cookieclient.modules.impl.player.NoFall;
import xxliam.cookieclient.modules.impl.render.AspectRatio;
import xxliam.cookieclient.modules.impl.render.Animations;
import xxliam.cookieclient.modules.impl.render.ESP;
import xxliam.cookieclient.modules.impl.render.FullBright;
import xxliam.cookieclient.modules.impl.render.NameProtect;
import xxliam.cookieclient.modules.impl.render.Theme;
import xxliam.cookieclient.modules.impl.render.hud.ClientElements;
import xxliam.cookieclient.modules.impl.render.hud.ModuleList;
import xxliam.cookieclient.modules.impl.render.hud.Notifications;
import xxliam.cookieclient.modules.impl.world.AutoTools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模块管理器：注册、查询、按键触发所有模块。
 * <p>
 * 对应 OpenZen 的 {@code shit.zen.manager.ModuleManager}。
 */
public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // 在这里注册所有模块
        add(new KillAura());
        add(new AntiKB());
        add(new Disabler());
        add(new AimAssist());
        add(new AutoClicker());
        add(new Sprint());
        add(new Scaffold());
        add(new NoDelay());
        add(new GuiMove());
        add(new NoFall());
        add(new ESP());
        add(new Theme());
        add(new ModuleList());
        add(new Notifications());
        add(new ClientElements());
        add(new FullBright());
        add(new AspectRatio());
        add(new NameProtect());
        add(new Animations());
        add(new AutoTools());
    }

    public void add(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModules(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public Module getModule(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public Module getModuleRequired(String name) throws ModuleNotFoundException {
        Module module = getModule(name);
        if (module == null) {
            throw new ModuleNotFoundException(name);
        }
        return module;
    }

    /**
     * 按键触发：与模块绑定的键被按下时调用。
     */
    public void onKeyPress(int key) {
        boolean toggled = false;
        for (Module module : modules) {
            if (module.getKeyBind() == key) {
                module.toggle();
                toggled = true;
            }
        }
        if (toggled && CookieClient.CONFIG_MANAGER != null) {
            CookieClient.CONFIG_MANAGER.save(); // 快捷键切换立即持久化
        }
    }

    /**
     * 每游戏刻驱动所有已启用模块。
     */
    public void onTick() {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick();
            }
        }
    }

    public Module getModuleIgnoreCase(String name) {
        return getModule(name == null ? null : name.toLowerCase(Locale.ROOT));
    }
}
