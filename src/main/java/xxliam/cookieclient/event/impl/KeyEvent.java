package xxliam.cookieclient.event.impl;

import xxliam.cookieclient.event.AbstractCancellable;

/**
 * 按键事件。可取消，用于拦截原版按键处理。
 */
public class KeyEvent extends AbstractCancellable {

    private final int key;
    private final int action;
    private final int modifiers;

    public KeyEvent(int key, int action, int modifiers) {
        this.key = key;
        this.action = action;
        this.modifiers = modifiers;
    }

    public int getKey() {
        return key;
    }

    public int getAction() {
        return action;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isPressed() {
        return action == 1; // GLFW_PRESS
    }
}
