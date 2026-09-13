package xxliam.cookieclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xxliam.cookieclient.hud.CookieClientVersion;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;

/**
 * 原版加载界面两处改造：
 * <ol>
 *   <li><b>背景</b>：Mojang 红（RGB 239,50,61）→ 纯黑。
 *       {@code BRAND_BACKGROUND} 是 {@code private static final IntSupplier}，
 *       {@code render} 里 3 处 {@code getAsInt()}（整屏背景、logo 底、进度条底）全部拦截；
 *       淡入淡出 alpha 由原版 {@code replaceAlpha} 在取色后叠加，不受影响。</li>
 *   <li><b>logo</b>：取消原版 Mojang 徽标的两次 blit（左右两半贴图），改为用
 *       {@link FontStore#momoSignature(float)}（灵动岛品牌同款 MomoSignature）绘制
 *       "Cookie"。注入点在原版 blit 调用处 —— 此刻原版刚 {@code GuiGraphics.setColor(1,1,1,logoAlpha)}
 *       设置了着色器颜色，position_tex_color shader 会把它乘进顶点色，因此文字的
 *       淡入/淡出与原 logo 完全同步，无需自算 alpha。</li>
 *   <li><b>进度条</b>：整条删除 —— 拦下 {@code render} 对私有 {@code drawProgressBar} 的
 *       唯一调用并取消。</li>
 * </ol>
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @ModifyExpressionValue(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/IntSupplier;getAsInt()I"
            )
    )
    private int cookieClient$blackLoadingBackground(int original) {
        return 0xFF000000;
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"
            )
    )
    private void cookieClient$cancelLogoAndDrawText(GuiGraphics guiGraphics, ResourceLocation atlas,
                                                    int x, int y, int width, int height,
                                                    float uOffset, float vOffset,
                                                    int uWidth, int vHeight,
                                                    int textureWidth, int textureHeight) {
        // 原版每帧调两次 blit：左半 uOffset=-0.0625f、右半 uOffset=+0.0625f。
        // 左半直接取消；在右半调用点画字 —— 每帧恰好执行一次，且此刻 shaderColor 已设为
        // (1,1,1,logoAlpha)，文字淡入/淡出与原 logo 完全同步。
        if (uOffset < 0.0f) {
            return;
        }

        final String text = CookieClientVersion.BRAND;
        final CustomFont font = FontStore.momoSignature(22.0f);

        float centerX = guiGraphics.guiWidth() / 2.0f;
        float centerY = guiGraphics.guiHeight() / 2.0f - 30.0f; // 与原 logo 一致：中心略偏上
        float textWidth = font.getStringWidth(text);
        // +1 = drawStringRGB 内部 translate(x, --y) 的盒顶上移（同 WatermarkHud.BOX_TOP_PIXEL_OFFSET）
        float baseline = centerY + 1.0f - cookieClient$inkCenterRelativeToBaseline(font, text);

        // alpha 传 1：淡入淡出由原版刚设置的 shaderColor(1,1,1,logoAlpha) 乘入
        font.drawStringRGB(guiGraphics.pose(), text, centerX - textWidth / 2.0f, baseline, 1.0f, 1.0f, 1.0f, 1.0f);

        // drawStringRGB 内部 disableCull，恢复到原版 blit 后的状态
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
    }

    /**
     * 进度条：整条删除 —— 拦下 {@code render} 里对原版 {@code drawProgressBar} 的唯一调用并取消。
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"
            )
    )
    private void cookieClient$removeProgressBar(LoadingOverlay overlay, GuiGraphics guiGraphics,
                                                int minX, int minY, int maxX, int maxY, float progress) {
        // no-op：不画进度条
    }

    /** 整串文字墨迹垂直中心相对基线的偏移（同 WatermarkHud.inkCenterRelativeToBaseline 的数学）。 */
    @Unique
    private static float cookieClient$inkCenterRelativeToBaseline(CustomFont font, String text) {
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                continue;
            }
            CustomFont.GlyphVisualBounds bounds = font.getGlyphRenderedBounds(c);
            if (bounds == null) {
                continue;
            }
            top = Math.min(top, bounds.y());
            bottom = Math.max(bottom, bounds.y() + bounds.height());
        }
        return bottom < top ? 0.0f : (top + bottom) / 2.0f;
    }
}
