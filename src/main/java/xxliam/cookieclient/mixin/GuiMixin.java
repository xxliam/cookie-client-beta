package xxliam.cookieclient.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.Renderer;

/**
 * HUD 渲染钩子：在 {@link Gui#render} 末尾驱动所有已启用模块的 {@link Module#render}。
 * <p>
 * 1.20.1 的 {@code Gui} 类没有 {@code getGuiGraphics()}，渲染上下文由该方法参数传入。
 */
@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void cookieClient$render(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        Renderer.markScreenBlurDirty(); // 本帧若需要后屏模糊（名牌底），先允许抓一次屏
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            if (module.isEnabled()) {
                module.render(guiGraphics, partialTick);
            }
        }
    }
}
