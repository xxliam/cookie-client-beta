package xxliam.cookieclient.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.Input;
import xxliam.cookieclient.gui.NewClickGui;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;

/**
 * GuiMove：打开 ClickGUI 时仍可移动（用物理按键状态覆盖移动输入，绕过 GUI 输入拦截）。
 * <p>
 * 实际由 {@code KeyboardInputMixin} 调用 {@link #onStrafe(Input)} 实现。
 */
public class GuiMove extends Module {

    public static GuiMove INSTANCE;

    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
        INSTANCE = this;
    }

    /** 由 KeyboardInputMixin 调用：GUI 打开时用物理按键覆盖移动输入。 */
    public static void onStrafe(Input input) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null || !INSTANCE.isMoving()) {
            return;
        }
        input.jumping = isMovementKey(mc.options.keyJump);
        input.forwardImpulse = getMovementSpeed(isMovementKey(mc.options.keyUp), isMovementKey(mc.options.keyDown));
        input.leftImpulse = getMovementSpeed(isMovementKey(mc.options.keyLeft), isMovementKey(mc.options.keyRight));
    }

    private boolean isMoving() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ChatScreen) {
            return false;
        }
        return mc.screen instanceof NewClickGui;
    }

    private static boolean isMovementKey(KeyMapping keyMapping) {
        Minecraft mc = Minecraft.getInstance();
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), keyMapping.getDefaultKey().getValue());
    }

    private static float getMovementSpeed(boolean forward, boolean back) {
        if (forward == back) {
            return 0.0f;
        }
        return forward ? 1.0f : -1.0f;
    }
}
