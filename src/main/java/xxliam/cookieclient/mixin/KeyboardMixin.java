package xxliam.cookieclient.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.NewClickGui;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownClickGui;
import xxliam.cookieclient.modules.impl.render.ClickGui;

/**
 * 键盘监听：游戏内按下绑定键（右 Shift 默认绑 ClickGui 模块）打开 / 关闭 ClickGUI、切换模块。
 * <p>
 * 打开与关闭完全对称，都直接走模块 toggle，不依赖 vanilla 把按键转发给 Screen：
 * <ul>
 *     <li>无 Screen：开关键 PRESS → 模块 toggle on → {@code onEnable} setScreen 打开 GUI。</li>
 *     <li>本客户端 ClickGUI 打开中：开关键 PRESS → 模块 toggle off → {@code onDisable}
 *         触发 Screen 的关闭动画，与打开路径互逆。</li>
 * </ul>
 * 两处都必须 {@code cancel()} 吞掉事件——Mixin 注入在 {@code KeyboardHandler.keyPress}
 * 的 HEAD，先于 vanilla 读取 {@code minecraft.screen}。若不吞掉，vanilla 会在同一个
 * {@code keyPress} 调用里把按键二次派发给刚打开的 Screen，Screen 把同一键当关闭键立即
 * {@code onClose()} → GUI「闪开即关」。同理打开期间按住的 REPEAT 也必须吞掉，避免重复触发。
 */
@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void cookieclient$onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || window != mc.getWindow().getWindow()) {
            return;
        }
        Screen screen = mc.screen;
        if (screen != null) {
            if (key != getClickGuiKey(screen)) {
                return; // 其它键（ESC/TAB/方向等）原样交给 vanilla → Screen
            }
            // 本客户端 ClickGUI 打开中：吞掉开关键的 PRESS / REPEAT。
            // PRESS = 模块 toggle off（onDisable 触发关闭动画）；REPEAT 仅吞掉防误触。
            if (action == GLFW.GLFW_PRESS && CookieClient.MODULE_MANAGER != null) {
                CookieClient.LOGGER.info("[Key] ClickGUI open, toggle key PRESS -> closing");
                CookieClient.MODULE_MANAGER.onKeyPress(key);
            }
            ci.cancel();
            return;
        }
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        if (CookieClient.MODULE_MANAGER != null) {
            CookieClient.MODULE_MANAGER.onKeyPress(key);
        }
        // 本次按键打开了 ClickGUI：吞掉事件，阻止 vanilla 在同一 keyPress 调用中
        // 把按键二次派发给新 Screen（见类注释），避免 GUI 闪开即关。
        if (mc.screen != null) {
            CookieClient.LOGGER.info("[Key] toggle key PRESS opened a GUI, swallowing vanilla re-dispatch");
            ci.cancel();
        }
    }

    /** 当前 Screen 若是本客户端的 ClickGUI，返回其开关键；否则返回 -1。 */
    private static int getClickGuiKey(Screen screen) {
        if (screen instanceof NewClickGui || screen instanceof DropdownClickGui) {
            return ClickGui.INSTANCE != null ? ClickGui.INSTANCE.getKeyBind() : -1;
        }
        return -1;
    }
}
