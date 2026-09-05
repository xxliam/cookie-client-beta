package xxliam.cookieclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.CookieClient;

/**
 * 鼠标监听：游戏内按下已绑定到模块的鼠标键（侧键 4~8 / GLFW 按钮码 3~7）时切换模块开关。
 * <p>
 * 左/中/右键仍完全保留给原版游戏行为（左键码 0 与本客户端的"未绑定"哨兵值冲突，
 * 本身也不可绑定），仅额外鼠标键用于触发绑定。与 {@link KeyboardMixin} 键位触发对称。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"))
    private void cookieclient$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || window != mc.getWindow().getWindow()) {
            return;
        }
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        // 仅在无界面、无覆盖层时触发绑定
        if (mc.screen != null || mc.getOverlay() != null) {
            return;
        }
        // 只认可额外鼠标键（GLFW_MOUSE_BUTTON_4=3 .. GLFW_MOUSE_BUTTON_LAST=7）
        if (button < GLFW.GLFW_MOUSE_BUTTON_4 || button > GLFW.GLFW_MOUSE_BUTTON_LAST) {
            return;
        }
        if (CookieClient.MODULE_MANAGER != null) {
            CookieClient.MODULE_MANAGER.onKeyPress(button);
        }
    }
}
