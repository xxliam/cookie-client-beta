package xxliam.cookieclient.modules;

import java.util.HashMap;
import java.util.Map;

/**
 * 按键绑定。存储模块与 GLFW 键码的映射关系。
 */
public class KeyBind {

    private final Map<Module, Integer> bindings = new HashMap<>();

    public void bind(Module module, int key) {
        bindings.put(module, key);
        module.setKeyBind(key);
    }

    public void unbind(Module module) {
        bindings.remove(module);
        module.setKeyBind(0);
    }

    public int get(Module module) {
        return bindings.getOrDefault(module, 0);
    }

    /**
     * 按键按下时调用，触发对应模块的开关。
     *
     * @param key GLFW 键码
     */
    public void onKeyPress(int key) {
        for (Map.Entry<Module, Integer> entry : bindings.entrySet()) {
            if (entry.getValue() == key) {
                entry.getKey().toggle();
            }
        }
    }
}
