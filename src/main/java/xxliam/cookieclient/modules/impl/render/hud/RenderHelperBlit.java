package xxliam.cookieclient.modules.impl.render.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * GuiGraphics sprite 绘制辅助（药水图标用，1.20.1 mojmap 适配）。
 */
final class RenderHelperBlit {

    private RenderHelperBlit() {
    }

    /**
     * 在 (x,y) 处绘制 sprite 到 width×height。
     *
     * @param blitOffset 通常传 0（对应 1.20.1 blit 的 z 参数）
     */
    static void blitSprite(GuiGraphics guiGraphics, TextureAtlasSprite sprite, int x, int y, int width, int height) {
        guiGraphics.blit(x, y, 0, width, height, sprite);
    }
}
