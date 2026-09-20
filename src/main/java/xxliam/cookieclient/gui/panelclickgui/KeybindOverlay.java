package xxliam.cookieclient.gui.panelclickgui;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.gui.panelclickgui.support.PanelCanvas;
import xxliam.cookieclient.gui.panelclickgui.support.PanelFonts;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.math.LerpUtil;
import xxliam.cookieclient.utils.render.RenderHelper;
import xxliam.cookieclient.utils.render.TextGlow;

public class KeybindOverlay {
    private static final Color OVERLAY_BG_COLOR = new Color(124, 124, 124, 13);
    private boolean isActive = false;
    private Module targetModule = null;
    private float alpha = 0.0f;
    private long startTime = 0L;

    // cookie 的 KeyBind 是「模块→键码」映射表，没有 OpenZen 那种单键码→可读名的静态表，
    // 这里逐字内联 OpenZen KeyBind 的静态映射与 getName 逻辑以保持 toast 文本完全一致。
    private static final Map<Integer, String> KEY_NAMES = new HashMap<>();
    static {
        KEY_NAMES.put(-1, "None");
        KEY_NAMES.put(32, "Space");
        KEY_NAMES.put(39, "'");
        KEY_NAMES.put(44, ",");
        KEY_NAMES.put(45, "-");
        KEY_NAMES.put(46, ".");
        KEY_NAMES.put(47, "/");
        KEY_NAMES.put(48, "0");
        KEY_NAMES.put(49, "1");
        KEY_NAMES.put(50, "2");
        KEY_NAMES.put(51, "3");
        KEY_NAMES.put(52, "4");
        KEY_NAMES.put(53, "5");
        KEY_NAMES.put(54, "6");
        KEY_NAMES.put(55, "7");
        KEY_NAMES.put(56, "8");
        KEY_NAMES.put(57, "9");
        KEY_NAMES.put(59, ";");
        KEY_NAMES.put(61, "=");
        KEY_NAMES.put(65, "A");
        KEY_NAMES.put(66, "B");
        KEY_NAMES.put(67, "C");
        KEY_NAMES.put(68, "D");
        KEY_NAMES.put(69, "E");
        KEY_NAMES.put(70, "F");
        KEY_NAMES.put(71, "G");
        KEY_NAMES.put(72, "H");
        KEY_NAMES.put(73, "I");
        KEY_NAMES.put(74, "J");
        KEY_NAMES.put(75, "K");
        KEY_NAMES.put(76, "L");
        KEY_NAMES.put(77, "M");
        KEY_NAMES.put(78, "N");
        KEY_NAMES.put(79, "O");
        KEY_NAMES.put(80, "P");
        KEY_NAMES.put(81, "Q");
        KEY_NAMES.put(82, "R");
        KEY_NAMES.put(83, "S");
        KEY_NAMES.put(84, "T");
        KEY_NAMES.put(85, "U");
        KEY_NAMES.put(86, "V");
        KEY_NAMES.put(87, "W");
        KEY_NAMES.put(88, "X");
        KEY_NAMES.put(89, "Y");
        KEY_NAMES.put(90, "Z");
        KEY_NAMES.put(91, "[");
        KEY_NAMES.put(92, "\\");
        KEY_NAMES.put(93, "]");
        KEY_NAMES.put(96, "`");
        KEY_NAMES.put(161, "W1");
        KEY_NAMES.put(162, "W2");
        KEY_NAMES.put(256, "Esc");
        KEY_NAMES.put(257, "Enter");
        KEY_NAMES.put(258, "Tab");
        KEY_NAMES.put(259, "Bksp");
        KEY_NAMES.put(260, "Ins");
        KEY_NAMES.put(261, "Del");
        KEY_NAMES.put(262, "Right");
        KEY_NAMES.put(263, "Left");
        KEY_NAMES.put(264, "Down");
        KEY_NAMES.put(265, "Up");
        KEY_NAMES.put(266, "PgUp");
        KEY_NAMES.put(267, "PgDn");
        KEY_NAMES.put(268, "Home");
        KEY_NAMES.put(269, "End");
        KEY_NAMES.put(280, "Caps");
        KEY_NAMES.put(281, "Scroll");
        KEY_NAMES.put(282, "NumLk");
        KEY_NAMES.put(283, "PrtSc");
        KEY_NAMES.put(284, "Pause");
        KEY_NAMES.put(290, "F1");
        KEY_NAMES.put(291, "F2");
        KEY_NAMES.put(292, "F3");
        KEY_NAMES.put(293, "F4");
        KEY_NAMES.put(294, "F5");
        KEY_NAMES.put(295, "F6");
        KEY_NAMES.put(296, "F7");
        KEY_NAMES.put(297, "F8");
        KEY_NAMES.put(298, "F9");
        KEY_NAMES.put(299, "F10");
        KEY_NAMES.put(300, "F11");
        KEY_NAMES.put(301, "F12");
        KEY_NAMES.put(320, "KP 0");
        KEY_NAMES.put(321, "KP 1");
        KEY_NAMES.put(322, "KP 2");
        KEY_NAMES.put(323, "KP 3");
        KEY_NAMES.put(324, "KP 4");
        KEY_NAMES.put(325, "KP 5");
        KEY_NAMES.put(326, "KP 6");
        KEY_NAMES.put(327, "KP 7");
        KEY_NAMES.put(328, "KP 8");
        KEY_NAMES.put(329, "KP 9");
        KEY_NAMES.put(330, "KP .");
        KEY_NAMES.put(331, "KP /");
        KEY_NAMES.put(332, "KP *");
        KEY_NAMES.put(333, "KP -");
        KEY_NAMES.put(334, "KP +");
        KEY_NAMES.put(335, "KP Enter");
        KEY_NAMES.put(336, "KP =");
        KEY_NAMES.put(340, "LShift");
        KEY_NAMES.put(341, "LCtrl");
        KEY_NAMES.put(342, "LAlt");
        KEY_NAMES.put(343, "LSuper");
        KEY_NAMES.put(344, "RShift");
        KEY_NAMES.put(345, "RCtrl");
        KEY_NAMES.put(346, "RAlt");
        KEY_NAMES.put(347, "RSuper");
        KEY_NAMES.put(348, "Menu");
    }

    private static String getKeyName(int keyCode) {
        if (keyCode == 0) {
            return "None";
        }
        return KEY_NAMES.getOrDefault(keyCode, "Unknown");
    }

    public void startBinding(Module module) {
        this.targetModule = module;
        this.isActive = true;
        this.startTime = System.currentTimeMillis();
    }

    public void cancel() {
        this.isActive = false;
        this.targetModule = null;
    }

    public boolean isVisible() {
        return this.isActive && this.alpha > 0.01f;
    }

    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float scale) {
        if (!this.isActive && this.alpha <= 0.005f) {
            return;
        }
        this.updateAlpha();
        if (this.alpha <= 0.005f) {
            return;
        }
        try {
            this.drawBackground(guiGraphics, screenWidth, screenHeight);
            float boxWidth = 400.0f * scale;
            float boxHeight = 180.0f * scale;
            int boxX = (int)(((float)screenWidth - boxWidth) / 2.0f);
            int boxY = (int)(((float)screenHeight - boxHeight) / 2.0f);
            this.drawGlow(guiGraphics, boxX, boxY, boxWidth, boxHeight, scale);
            this.drawContent(guiGraphics, boxX, boxY, boxWidth, scale);
        } catch (Exception exception) {
            // empty catch block
        }
    }

    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!this.isVisible()) {
            return false;
        }
        if (keyCode == 256) {
            if (this.targetModule != null) {
                this.targetModule.setKeyBind(-1);
                CookieClient.CONFIG_MANAGER.save();
                PanelClickGui.INSTANCE.addToast(this.targetModule.getName() + " keybind cleared");
            }
            this.cancel();
            return true;
        }
        if (this.targetModule != null && keyCode != -1) {
            this.targetModule.setKeyBind(keyCode);
            CookieClient.CONFIG_MANAGER.save();
            String keyName = getKeyName(keyCode);
            PanelClickGui.INSTANCE.addToast(this.targetModule.getName() + " bound to " + keyName.toUpperCase());
            this.cancel();
            return true;
        }
        return false;
    }

    private void updateAlpha() {
        this.alpha = this.isActive ? LerpUtil.lerp(this.alpha, 1.0f, 0.08f) : LerpUtil.lerp(this.alpha, 0.0f, 0.08f);
    }

    private void onRenderExtra() {
    }

    private void drawBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Color color = new Color(OVERLAY_BG_COLOR.getRed(), OVERLAY_BG_COLOR.getGreen(), OVERLAY_BG_COLOR.getBlue(), (int)((float)OVERLAY_BG_COLOR.getAlpha() * this.alpha));
        Renderer.drawRoundedRect(guiGraphics.pose(), 0.0f, 0.0f, screenWidth, screenHeight, 0.0f, color.getRGB());
    }

    private void drawGlow(GuiGraphics guiGraphics, int boxX, int boxY, float boxWidth, float boxHeight, float scale) {
        TextGlow.drawBackground(guiGraphics.pose(), boxX, boxY, boxWidth, boxHeight, 12.0f * scale, this.alpha);
    }

    private void drawContent(GuiGraphics guiGraphics, int boxX, int boxY, float boxWidth, float scale) {
        PanelCanvas canvas = new PanelCanvas(guiGraphics);
        int textColor;
        float textY;
        float textX;
        float textWidth;
        String text;
        CustomFont textFont;
        int alphaByte = (int)(255.0f * this.alpha);
        CustomFont titleFont = PanelFonts.axiformaBold(24.0f * scale);
        String title = "KEYBIND";
        float titleWidth = RenderHelper.getStringWidth(titleFont, title);
        float titleX = (float)boxX + (boxWidth - titleWidth) / 2.0f;
        float titleY = (float)boxY + 45.0f * scale;
        int titleColor = alphaByte << 24 | 0xFFFFFF;
        int glowColor = alphaByte << 24 | 0xFFFFFF;
        TextGlow.drawGlowText(guiGraphics.pose(), titleFont, title, titleX, titleY, titleColor, glowColor, 10.0f * scale);
        if (this.targetModule != null) {
            textFont = PanelFonts.axiformaRegular(18.0f * scale);
            text = "Module: " + this.targetModule.getName();
            textWidth = RenderHelper.getStringWidth(textFont, text);
            textX = (float)boxX + (boxWidth - textWidth) / 2.0f;
            textY = (float)boxY + 75.0f * scale;
            textColor = alphaByte << 24 | 0xFFFFFF;
            RenderHelper.drawText(guiGraphics.pose(), textFont, text, textX, textY, textColor);
        }
        textFont = PanelFonts.axiformaRegular(16.0f * scale);
        text = "Press any key to bind";
        textWidth = RenderHelper.getStringWidth(textFont, text);
        textX = (float)boxX + (boxWidth - textWidth) / 2.0f;
        textY = (float)boxY + 105.0f * scale;
        textColor = alphaByte << 24 | 0xCCCCCC;
        RenderHelper.drawText(guiGraphics.pose(), textFont, text, textX, textY, textColor);
        this.drawAnimatedDots(guiGraphics, boxX, (int)((float)boxY + 125.0f * scale), (int)boxWidth, alphaByte, scale);
        CustomFont cancelFont = PanelFonts.axiformaRegular(14.0f * scale);
        String cancelText = "Press ESC to cancel";
        float cancelWidth = RenderHelper.getStringWidth(cancelFont, cancelText);
        float cancelX = (float)boxX + (boxWidth - cancelWidth) / 2.0f;
        float cancelY = (float)boxY + 155.0f * scale;
        int cancelColor = alphaByte << 24 | 0xCCCCCC;
        RenderHelper.drawText(guiGraphics.pose(), cancelFont, cancelText, cancelX, cancelY, cancelColor);
        canvas.clearClipStack();
    }

    private void drawAnimatedDots(GuiGraphics guiGraphics, int boxX, int dotsY, int boxWidth, int alphaByte, float scale) {
        CustomFont dotFont = PanelFonts.axiformaBold(20.0f * scale);
        String dot = "•";
        float dotWidth = RenderHelper.getStringWidth(dotFont, dot);
        float totalWidth = dotWidth * 3.0f + 20.0f * scale;
        float startX = (float)boxX + ((float)boxWidth - totalWidth) / 2.0f;
        int dotColor = alphaByte << 24 | 0xFFFFFF;
        long now = System.currentTimeMillis();
        long elapsed = now - this.startTime;
        long cycleTime = elapsed % 1400L;
        for (int i = 0; i < 3; ++i) {
            float drawY;
            float dotX = startX + (float)i * (dotWidth + 10.0f * scale);
            long dotStart = (long)i * 150L;
            long dotEnd = dotStart + 300L;
            float verticalOffset = 0.0f;
            if (cycleTime >= dotStart && cycleTime <= dotEnd) {
                drawY = (float)(cycleTime - dotStart) / 300.0f;
                float angle = drawY * (float)Math.PI;
                verticalOffset = (float)(Math.sin(angle) * 6.0 * (double)scale);
            }
            drawY = (float)dotsY - verticalOffset;
            RenderHelper.drawText(guiGraphics.pose(), dotFont, dot, dotX, drawY, dotColor);
        }
    }

    public Module getTargetModule() {
        return this.targetModule;
    }

    static {
        new Color(255, 255, 255, 40);
    }
}
