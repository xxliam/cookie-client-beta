package xxliam.cookieclient.modules.impl.render;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * AspectRatio：自定义渲染宽高比（拉伸视野）。
 * <p>
 * 实际效果由 {@code GameRendererMixin} 注入 {@code getProjectionMatrix} 实现。
 */
public class AspectRatio extends Module {

    public static AspectRatio INSTANCE;
    public final NumberSetting ratioSetting = new NumberSetting("Ratio", 1.78, 0.1, 5.0, 0.1);

    public AspectRatio() {
        super("AspectRatio", Category.RENDER);
        INSTANCE = this;
        addSetting(ratioSetting);
    }
}
