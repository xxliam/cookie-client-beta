package xxliam.cookieclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xxliam.cookieclient.gui.mainmenu.MainMenuScreen;
import xxliam.cookieclient.gui.mainmenu.TitleScreenMode;

/**
 * 主页面入口（对应 Setsuna TitleScreenMixin）：
 * 原版模式在标题屏右上加 COOKIE UI 按钮；cookie 模式直接替换为主页面。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cookieClient$replaceTitle(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (TitleScreenMode.isVanilla()) {
            // 小巧的入口按钮：宽度按文字自适应，高度 20（原版按钮 27），贴右下角
            String label = "COOKIE UI";
            int buttonWidth = client.font.width(label) + 24;
            int buttonHeight = 20;
            int buttonX = Math.max(8, width - buttonWidth - 12);
            int buttonY = Math.max(8, height - buttonHeight - 12);
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                TitleScreenMode.useCookie();
                client.setScreen(new MainMenuScreen());
            }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());
            return;
        }
        client.setScreen(new MainMenuScreen());
    }
}
