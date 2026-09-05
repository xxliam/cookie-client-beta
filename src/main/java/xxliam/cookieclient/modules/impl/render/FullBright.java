package xxliam.cookieclient.modules.impl.render;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * FullBright：恒定亮度（zen 语义），亮度 0~100 可调。
 * <p>
 * 实际生效两个 hook（照搬 OpenZen GameRendererPatch + LivingEntityPatch）：
 * <ul>
 *   <li>{@code GameRendererMixin} 注入 {@code GameRenderer.getNightVisionScale}：
 *       启用时返回 Brightness/100；</li>
 *   <li>{@code LivingEntityMixin} 注入 {@code LivingEntity.hasEffect}：
 *       玩家伪造拥有 NIGHT_VISION（配合夜视判定，覆盖全屏与方块/实体渲染亮度）。</li>
 * </ul>
 * 后缀实时显示当前亮度值（对应 zen TripleProvider）。
 */
public class FullBright extends Module {

    public static FullBright INSTANCE;
    public final NumberSetting brightnessSetting = new NumberSetting("Brightness", 100.0, 0.0, 100.0, 1.0);

    public FullBright() {
        super("FullBright", Category.RENDER);
        INSTANCE = this;
        addSetting(brightnessSetting);
    }

    @Override
    public String getSuffix() {
        if (!isEnabled()) {
            return null;
        }
        return String.valueOf(brightnessSetting.getValue().intValue());
    }
}
