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
 * 键盘监听：按下右 Shift 打开 / 关闭 ClickGUI；游戏内按键触发已绑定模块的开关。
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
        // 游戏内（无界面）：触发绑定到该按键的模块开关
        if (mc.screen == null && CookieClient.MODULE_MANAGER != null) {
            CookieClient.MODULE_MANAGER.onKeyPress(key);
        }
        // 右 Shift：打开 / 关闭 ClickGUI
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (mc.screen == null) {
                mc.setScreen(new NewClickGui());
            } else if (mc.screen instanceof NewClickGui) {
                mc.screen.onClose();
            }
        }
    }
}
