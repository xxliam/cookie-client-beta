package xxliam.cookieclient.modules.impl.render;

import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;

/**
 * AntiNausea：反恶心（反「反胃」/CONFUSION）视觉效果。
 * <p>
 * 移植自 Naven-Modern {@code obsoverlay.modules.impl.render.AntiNausea}（ModuleInfo：
 * name = AntiNausea / category = RENDER，本体无任何设置与逻辑，纯开关）。
 * <p>
 * 生效方式与 Naven 一致：由 {@code LivingEntityMixin} 的 {@code hasEffect} HEAD 注入，
 * 对<b>本地玩家</b>伪造「没有 CONFUSION 效果」。1.20.1 恶心视觉的两处入口都走
 * {@code LocalPlayer.hasEffect(MobEffects.CONFUSION)}，故一处拦截即可全覆盖：
 * <ul>
 *   <li>{@code GameRenderer.render} → 门控 {@code renderConfusionOverlay}
 *       （屏幕上的 nausea.png 扭曲覆盖层）；</li>
 *   <li>{@code GameRenderer.renderLevel} → 门控视角扭曲因子（nausea warp）。</li>
 * </ul>
 * 只改客户端渲染判定，不改动 {@code MobEffectInstance} 本身，因此不影响任何服务端表现。
 */
public class AntiNausea extends Module {

    public static AntiNausea INSTANCE;

    public AntiNausea() {
        super("AntiNausea", Category.RENDER);
        INSTANCE = this;
    }
}
