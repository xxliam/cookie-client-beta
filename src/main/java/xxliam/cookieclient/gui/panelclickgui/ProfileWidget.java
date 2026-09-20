package xxliam.cookieclient.gui.panelclickgui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import xxliam.cookieclient.gui.panelclickgui.support.PanelCanvas;
import xxliam.cookieclient.gui.panelclickgui.support.PanelFonts;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.utils.math.LerpUtil;
import xxliam.cookieclient.utils.render.RenderHelper;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.render.TextGlow;

public class ProfileWidget {
    private float hoverAlpha = 0.0f;
    private boolean isHovered = false;
    private final SettingsPopup settingsPopup;

    public ProfileWidget(Consumer<Float> scaleChangeCallback) {
        this.settingsPopup = new SettingsPopup(scaleChangeCallback);
    }

    public void render(GuiGraphics guiGraphics, int originX, int originY, int mouseX, int mouseY, float scale, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            int avatarSize = (int)(20.0f * scale);
            int marginX = (int)(20.0f * scale);
            int textGap = (int)(10.0f * scale);
            int marginY = (int)(20.0f * scale);
            float cornerRadius = 6.0f * scale;
            int baseX = originX + marginX;
            int baseY = originY + marginY;
            String userId = this.getUserId();
            String userRole = this.getUserRole();
            int textX = baseX + avatarSize + textGap - (int)(11.0f * scale);
            int textY = baseY + avatarSize / 2 - (int)(10.0f * scale);
            int avatarX = textX - avatarSize - (int)(5.0f * scale);
            int avatarY = textY - (int)(8.0f * scale);
            this.checkHover(avatarX, avatarY, mouseX, mouseY, avatarSize);
            this.updateHoverAlpha();
            PanelCanvas canvas = new PanelCanvas(guiGraphics);
            if (this.hoverAlpha > 0.01f) {
                int hoverColor = new Color(255, 255, 255, (int)(30.0f * this.hoverAlpha * alpha)).getRGB();
                Renderer.drawRoundedRect(guiGraphics.pose(), avatarX - 2, avatarY - 2, avatarSize + 4, avatarSize + 4, cornerRadius + 1.0f, hoverColor);
            }
            if (mc.player instanceof AbstractClientPlayer) {
                RenderHelper.drawPlayerHeadRounded(guiGraphics.pose(), (AbstractClientPlayer) mc.player, avatarX, avatarY, avatarSize, avatarSize, alpha, cornerRadius);
            }
            CustomFont nameFont = PanelFonts.axiformaRegular(14.0f * scale);
            int glowColor = new Color(255, 255, 255, (int)(100.0f * alpha)).getRGB();
            TextGlow.drawGlowText(guiGraphics.pose(), nameFont, userId, textX, textY, this.applyAlpha(-1, alpha), glowColor, 8.0f * scale);

            float nameWidth = RenderHelper.getStringWidth(nameFont, userId);
            CustomFont roleFont = PanelFonts.axiformaBold(11.0f * scale);
            float roleStrWidth = RenderHelper.getStringWidth(roleFont, userRole);
            
            float roleBoxW = roleStrWidth + 8.0f * scale;
            float roleBoxH = 12.0f * scale;
            float roleBoxX = textX + nameWidth + 8.0f * scale;

            float roleBoxY = textY + (RenderHelper.getCapHeight(nameFont) - roleBoxH) / 2.0f;
            
            Color roleColor = this.getRoleColor(userRole);
            int roleShadowColor = new Color(roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue(), (int)(180.0f * alpha)).getRGB();
            
            Renderer.drawRoundedRect(guiGraphics.pose(), roleBoxX - 1.0f * scale, roleBoxY - 1.0f * scale, roleBoxW + 2.0f * scale, roleBoxH + 2.0f * scale, 5.0f * scale, this.applyAlpha(roleShadowColor, alpha * 0.35f));
            Renderer.drawRoundedRect(guiGraphics.pose(), roleBoxX, roleBoxY, roleBoxW, roleBoxH, 4.0f * scale, this.applyAlpha(roleColor.getRGB(), alpha));

            int roleTextGlow = new Color(255, 255, 255, (int)(120.0f * alpha)).getRGB();
            float roleTextX = roleBoxX + (roleBoxW - roleStrWidth) / 2.0f;
            float roleTextY = roleBoxY + (roleBoxH - RenderHelper.getCapHeight(roleFont)) / 2.0f + 0.5f * scale;
            TextGlow.drawGlowText(guiGraphics.pose(), roleFont, userRole, roleTextX, roleTextY, this.applyAlpha(-1, alpha), roleTextGlow, 5.0f * scale);
            canvas.clearClipStack();
            this.settingsPopup.render(guiGraphics, mouseX, mouseY, scale, alpha);
        } catch (Exception exception) {
            // empty catch block
        }
    }

    private int applyAlpha(int color, float alpha) {
        int origAlpha = color >> 24 & 0xFF;
        int newAlpha = (int)((float)origAlpha * alpha);
        return newAlpha << 24 | color & 0xFFFFFF;
    }

    private String getUserId() {
        // 用户名数据源：zen 的 ZenClient.username；cookie 无此字段，改用 Minecraft 登录名，空则兜底 "Unknown"（与 zen 一致）
        String name = Minecraft.getInstance().getUser().getName();
        return name != null && !name.isEmpty() ? name : "Unknown";
    }

    private String getUserRole() {
        // 照抄 zen 的死代码结构：if (false) 块恒不执行，运行时恒返回 "User"
        try {
            if (false) {
                List<String> roles = new ArrayList<>();
                if (roles.contains("ROLE_OWNER")) return "Premium";
                if (roles.contains("ROLE_ADMIN")) return "Admin";
                if (roles.contains("ROLE_BETA")) return "Beta";
                return roles.get(0).replace("ROLE_", "");
            }
        } catch (Exception exception) {
            // empty catch block
        }
        return "User";
    }

    public boolean isMouseOverAvatar(int originX, int originY, int mouseX, int mouseY, float scale) {
        int avatarSize = (int)(20.0f * scale);
        int marginX = (int)(20.0f * scale);
        int textGap = (int)(10.0f * scale);
        int marginY = (int)(20.0f * scale);
        int baseX = originX + marginX;
        int baseY = originY + marginY;
        int textX = baseX + avatarSize + textGap - (int)(11.0f * scale);
        int textY = baseY + avatarSize / 2 - (int)(10.0f * scale);
        int avatarX = textX - avatarSize - (int)(5.0f * scale);
        int avatarY = textY - (int)(8.0f * scale);
        return mouseX >= avatarX && mouseX <= avatarX + avatarSize && mouseY >= avatarY && mouseY <= avatarY + avatarSize;
    }

    public boolean onMouseClick(int originX, int originY, int mouseX, int mouseY, float scale) {
        if (this.settingsPopup.isOpen() && this.settingsPopup.onMouseClick(mouseX, mouseY, scale)) {
            return true;
        }
        if (this.isMouseOverAvatar(originX, originY, mouseX, mouseY, scale)) {
            this.settingsPopup.toggleOpen();
            return true;
        }
        return false;
    }

    private Color getRoleColor(String role) {
        switch (role.toLowerCase()) {
            case "owner":
                return new Color(220, 53, 69);
            case "admin":
                return new Color(255, 193, 7);
            case "beta":
                return new Color(108, 117, 225);
            case "vip":
                return new Color(40, 167, 69);
            case "premium":
                return new Color(102, 16, 242);
            default:
                return new Color(108, 117, 125);
        }
    }

    private void checkHover(int avatarX, int avatarY, int mouseX, int mouseY, int avatarSize) {
        this.isHovered = mouseX >= avatarX && mouseX <= avatarX + avatarSize && mouseY >= avatarY && mouseY <= avatarY + avatarSize;
    }

    private void updateHoverAlpha() {
        this.hoverAlpha = this.isHovered ? LerpUtil.lerp(this.hoverAlpha, 1.0f, 0.12f) : LerpUtil.lerp(this.hoverAlpha, 0.0f, 0.12f);
    }

    public void onMouseDrag(int mouseX, int mouseY) {
        this.settingsPopup.onMouseDrag(mouseX, mouseY);
    }

    public void onMouseRelease() {
        this.settingsPopup.stopDrag();
    }

    public boolean isPopupOpen() {
        return this.settingsPopup.isOpen();
    }
}
