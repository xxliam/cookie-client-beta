package xxliam.cookieclient.gui.newclickgui.input;

import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.gui.newclickgui.BindElement;

/**
 * Bind 控件的交互状态与事件处理（原散落在 {@code BindElement} 里的静态监听态）。
 * <p>
 * 全局同一时刻最多一个 {@link BindElement} 处于监听态：进入监听后，后续的键盘 / 鼠标侧键
 * 事件由本处理器消费并写入 {@code Module.setKeyBind(int)}。
 */
public final class BindInputHandler {

    private static BindElement listening;

    private BindInputHandler() {
    }

    /** 该控件当前是否处于监听态（渲染用它显示 {@code ...}）。 */
    public static boolean isListening(BindElement element) {
        return listening == element;
    }

    public static boolean hasListening() {
        return listening != null;
    }

    /** 清除监听态（GUI 关闭 / 折叠 / 点击按钮时调用，避免残留引用）。 */
    public static void clear() {
        listening = null;
    }

    /** 进入监听态。 */
    static void begin(BindElement element) {
        listening = element;
    }

    /** 退出监听态。 */
    static void end() {
        listening = null;
    }

    /** 翻转某控件的监听态（点击 Bind 行）。 */
    static void toggle(BindElement element) {
        if (listening == element) {
            end();
        } else {
            begin(element);
        }
    }

    /**
     * 监听态下处理按键。
     *
     * @param key GLFW 键码
     * @return true = 已消费该按键
     */
    public static boolean keyPressed(int key) {
        if (listening == null) {
            return false;
        }
        // ESC / Delete / Backspace 解绑（清空当前按键绑定）
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            listening.bind(0);
            end();
            return true;
        }
        listening.bind(key);
        end();
        return true;
    }

    /**
     * 监听态下处理鼠标键。仅认可额外鼠标键（侧键 4~8，GLFW 按钮码 3~7）；
     * 左键(0)与「未绑定」哨兵冲突，中/右键保留给原版交互，均不可绑定。
     *
     * @param button GLFW 鼠标按钮码
     * @return true = 已消费该按键
     */
    public static boolean mousePressed(int button) {
        if (listening == null || !BindElement.isBindableMouseButton(button)) {
            return false;
        }
        listening.bind(button);
        end();
        return true;
    }
}
