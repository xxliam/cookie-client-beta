package xxliam.cookieclient.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.NewClickGui;

/**
 * 键盘监听：游戏内按右 Shift 打开 ClickGUI，按绑定键触发已绑定模块的开关。
 * <p>
 * 仅当没有 Screen 打开时生效（界面打开时按键全部交给 {@code Screen.keyPressed}，
 * ClickGUI 自身的关闭 / Bind 监听都走 GUI 事件，不在此处处理）。
 */
@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void cookieclient$onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || window != mc.getWindow().getWindow()) {
            return;
        }
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            mc.setScreen(new NewClickGui());
            return;
        }
        if (CookieClient.MODULE_MANAGER != null) {
            CookieClient.MODULE_MANAGER.onKeyPress(key);
        }
    }
}
