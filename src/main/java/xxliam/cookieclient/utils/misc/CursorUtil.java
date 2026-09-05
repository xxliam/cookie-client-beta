package xxliam.cookieclient.utils.misc;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * 光标与命中判定工具。
 * <p>
 * 仿 OpenZen 的 {@code shit.zen.utils.misc.CursorUtil}。
 */
public final class CursorUtil {

    private static final Map<Integer, Long> CURSOR_CACHE = new HashMap<>();

    private CursorUtil() {
    }

    public static boolean isInBounds(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX > x && mouseY > y && mouseX < x + width && mouseY < y + height;
    }

    public static void setCursor(int shape) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        long cursor = CURSOR_CACHE.computeIfAbsent(shape, GLFW::glfwCreateStandardCursor);
        GLFW.glfwSetCursor(window, cursor);
    }

    public static void setDefaultCursor() {
        setCursor(0x36001); // GLFW_ARROW_CURSOR
    }
}
