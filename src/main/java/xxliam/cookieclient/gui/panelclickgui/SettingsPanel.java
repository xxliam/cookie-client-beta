package xxliam.cookieclient.gui.panelclickgui;

import java.awt.Color;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.panelclickgui.PanelClickGui;
import xxliam.cookieclient.gui.panelclickgui.support.PanelCanvas;
import xxliam.cookieclient.gui.panelclickgui.support.PanelFonts;
import xxliam.cookieclient.gui.panelclickgui.setting.SettingRenderer;
import xxliam.cookieclient.gui.panelclickgui.setting.SettingRendererRegistry;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.Rectangle;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.render.StencilHelper;
import xxliam.cookieclient.utils.render.TextGlow;
import xxliam.cookieclient.settings.Setting;
import xxliam.cookieclient.utils.math.LerpUtil;
import xxliam.cookieclient.utils.render.RenderHelper;

public class SettingsPanel {
    public enum AnimationState { NONE, FADE_IN, FADE_OUT, SWITCHING }

    private static final int PANEL_BG_COLOR = new Color(255, 255, 255, 20).getRGB();
    private static final int TOGGLE_ON_COLOR = new Color(76, 175, 80).getRGB();
    private static final int TOGGLE_OFF_COLOR = new Color(158, 158, 158).getRGB();
    private Module currentModule;
    private boolean isToggleHovered = false;
    private float enabledAlpha = 0.0f;
    private float toggleHoverAlpha = 0.0f;
    private float scrollOffset = 0.0f;
    private float scrollTarget = 0.0f;
    private float totalContentHeight = 0.0f;
    private boolean isDraggingScrollbar = false;
    private float scrollbarDragStartY = 0.0f;
    private float scrollOffsetAtDragStart = 0.0f;
    private float scrollbarAlpha = 0.0f;
    private long lastScrollTime = 0L;
    private Module prevModule;
    private SettingsPanel.AnimationState animationState = SettingsPanel.AnimationState.NONE;
    private float transitionProgress = 0.0f;
    private float lastScale = 1.0f;

    public void render(GuiGraphics guiGraphics, int originX, int originY, int mouseX, int mouseY, Module module, float scale, float alpha) {
        if (Math.abs(scale - this.lastScale) > 0.001f) {
            this.rescaleScroll(scale);
            this.lastScale = scale;
        }
        if (this.currentModule != module) {
            if (this.currentModule != null) {
                this.prevModule = this.currentModule;
                this.animationState = SettingsPanel.AnimationState.FADE_OUT;
                this.transitionProgress = 0.0f;
            } else if (module != null) {
                this.animationState = SettingsPanel.AnimationState.FADE_IN;
                this.transitionProgress = 0.0f;
                this.prevModule = null;
            }
            this.currentModule = module;
            this.scrollOffset = 0.0f;
            this.scrollTarget = 0.0f;
        }
        if (this.animationState != SettingsPanel.AnimationState.NONE) {
            this.transitionProgress = LerpUtil.smoothLerp(this.transitionProgress, 1.0f, 0.22f);
            if (this.transitionProgress > 0.99f) {
                this.transitionProgress = 1.0f;
                if (this.animationState == SettingsPanel.AnimationState.FADE_OUT) {
                    this.prevModule = null;
                    if (this.currentModule != null) {
                        this.animationState = SettingsPanel.AnimationState.FADE_IN;
                        this.transitionProgress = 0.0f;
                    } else {
                        this.animationState = SettingsPanel.AnimationState.NONE;
                    }
                } else {
                    this.animationState = SettingsPanel.AnimationState.NONE;
                }
            }
        }
        if (this.currentModule == null && this.animationState == SettingsPanel.AnimationState.NONE) {
            return;
        }
        this.scrollOffset = Math.abs(this.scrollOffset - this.scrollTarget) > 0.01f ? LerpUtil.smoothLerp(this.scrollOffset, this.scrollTarget, 0.35f) : this.scrollTarget;
        float enabledTarget = this.currentModule != null && this.currentModule.isEnabled() ? 1.0f : 0.0f;
        this.enabledAlpha = Math.abs(enabledTarget - this.enabledAlpha) > 0.01f ? LerpUtil.smoothLerp(this.enabledAlpha, enabledTarget, 0.28f) : enabledTarget;
        try {
            PanelCanvas canvas = new PanelCanvas(guiGraphics);
            int panelWidth = (int)(405.0f * scale);
            int marginX = (int)(20.0f * scale);
            int marginY = (int)(20.0f * scale);
            int baseSize = (int)(400.0f * scale);
            int headerHeight = (int)(30.0f * scale);
            int bottomPadding = (int)(10.0f * scale);
            int panelX = originX + (int)(600.0f * scale) - panelWidth - marginX + (int)(8.0f * scale);
            int panelY = originY + marginY + (int)(23.0f * scale);
            int panelHeight = baseSize - 2 * marginX - (int)(20.0f * scale);
            int toggleHeight = (int)(12.0f * scale);
            int toggleRightPadding = (int)(15.0f * scale);
            int toggleX = panelX + panelWidth - toggleHeight * 2 - toggleRightPadding;
            int toggleY = panelY + (headerHeight - toggleHeight) / 2;
            this.checkToggleHover(toggleX, toggleY, mouseX, mouseY, toggleHeight);
            float toggleHoverTarget = this.isToggleHovered ? 1.0f : 0.0f;
            this.toggleHoverAlpha = Math.abs(toggleHoverTarget - this.toggleHoverAlpha) > 0.01f ? LerpUtil.smoothLerp(this.toggleHoverAlpha, toggleHoverTarget, 0.35f) : toggleHoverTarget;
            Renderer.drawRoundedRect(guiGraphics.pose(), panelX, panelY, panelWidth, panelHeight, 4.0f * scale, this.applyAlpha(PANEL_BG_COLOR, alpha));
            this.renderToggleButton(guiGraphics, toggleX, toggleY, this.enabledAlpha, this.toggleHoverAlpha, this.currentModule != null && this.currentModule.isEnabled(), scale, alpha);
            int stencilX = panelX;
            int stencilY = panelY + headerHeight;
            int stencilWidth = panelWidth;
            int stencilHeight = panelHeight - headerHeight;
            StencilHelper.clipRounded(guiGraphics.pose(), stencilX, stencilY, stencilWidth, stencilHeight, 4.0f * scale, () -> {
                this.calculateTotalHeight(this.currentModule, scale);
                canvas.save();
                canvas.clip(Rectangle.ofXYWH(panelX, panelY, panelWidth, panelHeight));
                Module renderModule = this.animationState == SettingsPanel.AnimationState.FADE_OUT ? this.prevModule : this.currentModule;
                float titleAlpha = this.animationState == SettingsPanel.AnimationState.FADE_OUT ? (1.0f - this.transitionProgress) * alpha : alpha;
                if (renderModule != null) {
                    CustomFont titleFont = PanelFonts.axiformaBold(20.0f * scale);
                    String title = renderModule.getName();
                    if (renderModule.isEnabled()) {
                        int glowColor = this.applyAlpha(new Color(255, 255, 255, 150).getRGB(), titleAlpha);
                        TextGlow.drawGlowText(guiGraphics.pose(), titleFont, title, (float)panelX + 10.0f * scale, (float)panelY + 12.0f * scale, this.applyAlpha(-1, titleAlpha), glowColor, 12.0f * scale);
                    } else {
                        RenderHelper.drawText(guiGraphics.pose(), titleFont, title, (float)panelX + 10.0f * scale, (float)panelY + 12.0f * scale, this.applyAlpha(-1, titleAlpha));
                    }
                }
                canvas.restore();
                canvas.save();
                canvas.clip(Rectangle.ofXYWH(panelX, panelY + headerHeight, panelWidth, panelHeight - headerHeight));
                float slideY = 0.0f;
                float bodyAlpha = alpha;
                Module bodyModule;
                if (this.animationState == SettingsPanel.AnimationState.FADE_OUT && this.prevModule != null) {
                    bodyModule = this.prevModule;
                    slideY = this.transitionProgress * 30.0f * scale;
                    bodyAlpha = (1.0f - this.transitionProgress) * alpha;
                } else if (this.animationState == SettingsPanel.AnimationState.FADE_IN && this.currentModule != null) {
                    bodyModule = this.currentModule;
                    slideY = (1.0f - this.transitionProgress) * -30.0f * scale;
                    bodyAlpha = this.transitionProgress * alpha;
                } else {
                    bodyModule = this.currentModule;
                }
                if (bodyModule != null && bodyAlpha > 0.01f) {
                    canvas.save();
                    // zen 里 drawContext.translate 与 guiGraphics.pose().translate 是两套独立 PoseStack
                    // （zen 的 DrawContext 自建 new PoseStack()），各施加一次；
                    // cookie 的 PanelCanvas 直接绑 guiGraphics.pose()，两行会叠加成 2×slideY，
                    // 故这里只保留 canvas.translate —— 位移量与 zen 观感一致（30px × scale）。
                    canvas.translate(0.0f, slideY);
                    List<Setting<?>> settings = bodyModule.getSettings();
                    if (settings != null && !settings.isEmpty()) {
                        int settingY = panelY + headerHeight - (int)this.scrollOffset;
                        for (Setting<?> setting : settings) {
                            if (setting.getVisibility() != null && !setting.getVisibility().displayable()) continue;
                            int dy = SettingRendererRegistry.getInstance().render(guiGraphics, setting, panelX + (int)(10.0f * scale), settingY, panelWidth - (int)(20.0f * scale), mouseX, mouseY, bodyAlpha, scale);
                            settingY += dy;
                        }
                    } else {
                        String description = this.getModuleDescription(bodyModule);
                        if (description != null && !description.isEmpty()) {
                            CustomFont descFont = PanelFonts.axiformaRegular(12.0f * scale);
                            this.renderWrappedText(guiGraphics, description, panelX + (int)(10.0f * scale), panelY + headerHeight + (int)(10.0f * scale), panelWidth - (int)(20.0f * scale), descFont, -5592406, bodyAlpha, scale);
                        }
                    }
                    canvas.restore();
                }
                canvas.restore();
            });
            canvas.clearClipStack();
            int contentHeight = baseSize - 2 * marginX - (int)(20.0f * scale);
            float visibleHeight = contentHeight - headerHeight - bottomPadding;
            if (this.totalContentHeight > visibleHeight) {
                float maxScroll = this.totalContentHeight - visibleHeight;
                if (this.scrollOffset > maxScroll) {
                    this.scrollOffset = maxScroll;
                    this.scrollTarget = maxScroll;
                }
                if (this.scrollTarget > maxScroll) {
                    this.scrollTarget = maxScroll;
                }
            } else {
                this.scrollOffset = 0.0f;
                this.scrollTarget = 0.0f;
            }
            this.renderScrollbar(guiGraphics, panelX, panelY, panelHeight, scale, alpha);
        } catch (Exception exception) {
            // empty catch block
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int panelX, int panelY, int panelHeight, float scale, float alpha) {
        float targetAlpha;
        int headerHeight = (int)(30.0f * scale);
        int bottomPadding = (int)(10.0f * scale);
        float visibleHeight = panelHeight - headerHeight - bottomPadding;
        if (this.totalContentHeight <= visibleHeight) {
            targetAlpha = 0.0f;
        } else {
            long sinceScroll = System.currentTimeMillis() - this.lastScrollTime;
            if (this.isDraggingScrollbar || sinceScroll < 500L) {
                targetAlpha = 1.0f;
            } else if (sinceScroll < 1000L) {
                long fadeMs = sinceScroll - 500L;
                targetAlpha = 1.0f - (float)fadeMs / 500.0f;
            } else {
                targetAlpha = 0.0f;
            }
        }
        this.scrollbarAlpha = Math.abs(this.scrollbarAlpha - targetAlpha) > 0.01f ? LerpUtil.smoothLerp(this.scrollbarAlpha, targetAlpha, 0.35f) : targetAlpha;
        if (this.scrollbarAlpha <= 0.01f) {
            return;
        }
        float maxScroll = this.totalContentHeight - visibleHeight;
        if (maxScroll <= 0.0f) {
            return;
        }
        float thumbHeight = Math.max(20.0f * scale, visibleHeight / this.totalContentHeight * visibleHeight);
        float thumbY = (float)(panelY + headerHeight) + this.scrollOffset / maxScroll * (visibleHeight - thumbHeight);
        int thumbX = panelX + (int)(405.0f * scale) - (int)(4.0f * scale) - 2;
        float thumbWidth = 4.0f * scale;
        int thumbColor = new Color(1.0f, 1.0f, 1.0f, this.scrollbarAlpha * alpha).getRGB();
        Renderer.drawRoundedRect(guiGraphics.pose(), thumbX, thumbY, thumbWidth, thumbHeight, thumbWidth / 2.0f, thumbColor);
    }

    private void checkToggleHover(int toggleX, int toggleY, int mouseX, int mouseY, int toggleHeight) {
        this.isToggleHovered = mouseX >= toggleX && mouseX <= toggleX + toggleHeight * 2 && mouseY >= toggleY && mouseY <= toggleY + toggleHeight;
    }

    private void renderToggleButton(GuiGraphics guiGraphics, int toggleX, int toggleY, float enabledFactor, float hoverFactor, boolean enabled, float scale, float alpha) {
        int overlayColor;
        int unit = (int)(12.0f * scale);
        int toggleWidth = unit * 2;
        int toggleHeight = unit;
        int trackColor = this.lerpColor(TOGGLE_OFF_COLOR, TOGGLE_ON_COLOR, enabledFactor);
        if (hoverFactor > 0.0f) {
            float brightness = 1.0f + 0.3f * hoverFactor;
            trackColor = this.brightenColor(trackColor, brightness);
        }
        int trackColorAlpha = this.applyAlpha(trackColor, alpha);
        if (enabled) {
            overlayColor = this.applyAlpha(new Color(76, 175, 80, 70).getRGB(), alpha);
            Renderer.drawRoundedRect(guiGraphics.pose(), (float)toggleX - scale, (float)toggleY - scale, (float)toggleWidth + 2.0f * scale, (float)toggleHeight + 2.0f * scale, (float)toggleHeight / 2.0f, overlayColor);
        }
        Renderer.drawRoundedRect(guiGraphics.pose(), toggleX, toggleY, toggleWidth, toggleHeight, (float)toggleHeight / 2.0f, trackColorAlpha);
        overlayColor = unit - (int)(4.0f * scale);
        int knobInset = (toggleHeight - overlayColor) / 2;
        int knobMinX = toggleX + knobInset;
        int knobMaxX = toggleX + toggleWidth - overlayColor - knobInset;
        int knobX = knobMinX + (int)((float)(knobMaxX - knobMinX) * enabledFactor);
        int knobY = toggleY + knobInset;
        if (hoverFactor > 0.0f) {
            int highlightAlpha = (int)(50.0f * hoverFactor);
            int highlightColor = highlightAlpha << 24 | 0xFFFFFF;
            float highlightSize = (float)overlayColor + 2.0f * scale;
            Renderer.drawRoundedRect(guiGraphics.pose(), knobX - 1, knobY - 1, highlightSize, highlightSize, highlightSize / 2.0f, this.applyAlpha(highlightColor, alpha));
        }
        Renderer.drawRoundedRect(guiGraphics.pose(), knobX, knobY, overlayColor, overlayColor, (float)overlayColor / 2.0f, this.applyAlpha(-1, alpha));
    }

    private int renderWrappedText(GuiGraphics guiGraphics, String text, int x, int y, int maxWidth, CustomFont fontRenderer, int textColor, float alpha, float scale) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int currentY = y;
        int lineHeight = (int)(16.0f * scale);
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            float candidateWidth = RenderHelper.getStringWidth(fontRenderer, candidate);
            if (candidateWidth > (float)maxWidth && line.length() > 0) {
                RenderHelper.drawText(guiGraphics.pose(), fontRenderer, line.toString(), x, currentY, this.applyAlpha(textColor, alpha));
                line = new StringBuilder(word);
                currentY += lineHeight;
                continue;
            }
            line = new StringBuilder(candidate);
        }
        if (line.length() > 0) {
            RenderHelper.drawText(guiGraphics.pose(), fontRenderer, line.toString(), x, currentY, this.applyAlpha(textColor, alpha));
        }
        return currentY - y + lineHeight;
    }

    private String getModuleDescription(Module module) {
        try {
            return "This module provides " + module.getName().toLowerCase() + " functionality.";
        } catch (Exception exception) {
            return "No description available.";
        }
    }

    private void calculateTotalHeight(Module module, float scale) {
        if (module == null) {
            this.totalContentHeight = 0.0f;
            return;
        }
        List<Setting<?>> settings = module.getSettings();
        if (settings != null && !settings.isEmpty()) {
            int total = 0;
            for (Setting setting : settings) {
                if (setting.getVisibility() != null && !setting.getVisibility().displayable()) continue;
                total += SettingRendererRegistry.getInstance().getHeightForScroll(setting, scale);
            }
            this.totalContentHeight = total;
        } else {
            String description = this.getModuleDescription(module);
            if (description != null && !description.isEmpty()) {
                CustomFont fontRenderer = PanelFonts.axiformaRegular(12.0f * scale);
                this.totalContentHeight = this.calcWrappedTextHeight(description, (int)(385.0f * scale), fontRenderer, scale);
            } else {
                this.totalContentHeight = 0.0f;
            }
        }
    }

    private int calcWrappedTextHeight(String text, int maxWidth, CustomFont fontRenderer, float scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 1;
        int lineHeight = (int)(16.0f * scale);
        for (String word : words) {
            String candidate;
            String dup = candidate = line.length() == 0 ? word : line + " " + word;
            if (RenderHelper.getStringWidth(fontRenderer, candidate) > (float)maxWidth && line.length() > 0) {
                ++lines;
                line = new StringBuilder(word);
                continue;
            }
            line = new StringBuilder(candidate);
        }
        return lines * lineHeight;
    }

    private int applyAlpha(int color, float alpha) {
        int origAlpha = color >> 24 & 0xFF;
        int newAlpha = (int)((float)origAlpha * alpha);
        return newAlpha << 24 | color & 0xFFFFFF;
    }

    private int brightenColor(int color, float factor) {
        int a = color >> 24 & 0xFF;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int)((float)r * factor));
        g = Math.min(255, (int)((float)g * factor));
        b = Math.min(255, (int)((float)b * factor));
        return a << 24 | r << 16 | g << 8 | b;
    }

    public boolean onMouseClick(int originX, int originY, int mouseX, int mouseY, int button, float scale) {
        if (this.animationState != SettingsPanel.AnimationState.NONE) {
            return true;
        }
        if (this.currentModule == null) {
            return false;
        }
        int panelWidth = (int)(405.0f * scale);
        int marginX = (int)(20.0f * scale);
        int marginY = (int)(20.0f * scale);
        int baseSize = (int)(400.0f * scale);
        int headerHeight = (int)(30.0f * scale);
        int bottomPadding = (int)(10.0f * scale);
        int panelX = originX + (int)(600.0f * scale) - panelWidth - marginX + (int)(8.0f * scale);
        int panelY = originY + marginY + (int)(23.0f * scale);
        int panelHeight = baseSize - 2 * marginX - (int)(20.0f * scale);
        float visibleHeight = panelHeight - headerHeight - bottomPadding;
        if (this.totalContentHeight > visibleHeight) {
            float maxScroll = this.totalContentHeight - visibleHeight;
            float thumbHeight = Math.max(20.0f * scale, visibleHeight / this.totalContentHeight * visibleHeight);
            float thumbY = (float)(panelY + headerHeight) + this.scrollOffset / maxScroll * (visibleHeight - thumbHeight);
            float thumbWidth = 4.0f * scale;
            int thumbX = panelX + panelWidth - (int)thumbWidth - 2;
            if (mouseX >= thumbX && (float)mouseX <= (float)thumbX + thumbWidth && (float)mouseY >= thumbY && (float)mouseY <= thumbY + thumbHeight) {
                this.isDraggingScrollbar = true;
                this.scrollbarDragStartY = mouseY;
                this.scrollOffsetAtDragStart = this.scrollOffset;
                this.lastScrollTime = System.currentTimeMillis();
                return true;
            }
        }
        int toggleHeight = (int)(12.0f * scale);
        int toggleRightPadding = (int)(15.0f * scale);
        int toggleX = panelX + panelWidth - toggleHeight * 2 - toggleRightPadding;
        int toggleY = panelY + (headerHeight - toggleHeight) / 2;
        if (button == 0 && mouseX >= toggleX && mouseX <= toggleX + toggleHeight * 2 && mouseY >= toggleY && mouseY <= toggleY + toggleHeight) {
            this.currentModule.toggle();
            String stateLabel = this.currentModule.isEnabled() ? "On" : "Off";
            PanelClickGui.INSTANCE.addToast(this.currentModule.getName() + " Module " + stateLabel);
            return true;
        }
        List<Setting<?>> settings = this.currentModule.getSettings();
        if (settings != null && !settings.isEmpty()) {
            int settingY = panelY + headerHeight;
            int adjustedMouseY = mouseY + (int)this.scrollOffset;
            for (Setting setting : settings) {
                if (setting.getVisibility() != null && !setting.getVisibility().displayable()) continue;
                int settingHeight = SettingRendererRegistry.getInstance().getHeightForScroll(setting, scale);
                if (adjustedMouseY >= settingY && adjustedMouseY <= settingY + settingHeight && SettingRendererRegistry.getInstance().onClick(setting, panelX + (int)(10.0f * scale), settingY - (int)this.scrollOffset, panelWidth - (int)(20.0f * scale), mouseX, mouseY, button, scale)) {
                    return true;
                }
                settingY += settingHeight;
            }
        }
        return false;
    }

    public boolean isMouseOverPanel(int originX, int originY, int mouseX, int mouseY, float scale) {
        if (this.currentModule == null) {
            return false;
        }
        int panelWidth = (int)(405.0f * scale);
        int marginX = (int)(20.0f * scale);
        int marginY = (int)(20.0f * scale);
        int baseSize = (int)(400.0f * scale);
        int panelX = originX + (int)(600.0f * scale) - panelWidth - marginX + (int)(8.0f * scale);
        int panelY = originY + marginY + (int)(23.0f * scale);
        int panelHeight = baseSize - 2 * marginX - (int)(20.0f * scale);
        return mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    public void onScroll(double scrollDelta, float scale) {
        int contentHeight = (int)(400.0f * scale) - (int)(40.0f * scale) - (int)(20.0f * scale);
        float visibleHeight = (float)contentHeight - 30.0f * scale - 10.0f * scale;
        if (this.totalContentHeight > visibleHeight) {
            float maxScroll = this.totalContentHeight - visibleHeight;
            this.scrollTarget -= (float)scrollDelta * 18.0f * scale;
            this.scrollTarget = Math.max(0.0f, Math.min(this.scrollTarget, maxScroll));
            this.lastScrollTime = System.currentTimeMillis();
        }
    }

    public void onMouseDrag(double mouseX, double mouseY, float scale) {
        if (this.isDraggingScrollbar) {
            int contentHeight = (int)(400.0f * scale) - (int)(40.0f * scale) - (int)(20.0f * scale);
            float visibleHeight = (float)contentHeight - 30.0f * scale - 10.0f * scale;
            float maxScroll = this.totalContentHeight - visibleHeight;
            float thumbHeight = Math.max(20.0f * scale, visibleHeight / this.totalContentHeight * visibleHeight);
            float trackHeight = visibleHeight - thumbHeight;
            if (trackHeight > 0.0f) {
                float dragDeltaY = (float)mouseY - this.scrollbarDragStartY;
                float scrollDelta = dragDeltaY / trackHeight * maxScroll;
                this.scrollOffset = this.scrollOffsetAtDragStart + scrollDelta;
                this.scrollTarget = this.scrollOffset = Math.max(0.0f, Math.min(this.scrollOffset, maxScroll));
            }
            this.lastScrollTime = System.currentTimeMillis();
        }
        if (this.currentModule != null) {
            for (Setting setting : this.currentModule.getSettings()) {
                SettingRenderer settingRenderer;
                if (setting.getVisibility() != null && !setting.getVisibility().displayable() || (settingRenderer = SettingRendererRegistry.getInstance().findRenderer(setting)) == null) continue;
                settingRenderer.onMouseMove(mouseX, mouseY);
            }
        }
    }

    public void onMouseRelease(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        this.lastScrollTime = System.currentTimeMillis();
        if (this.currentModule != null) {
            for (Setting setting : this.currentModule.getSettings()) {
                SettingRenderer settingRenderer;
                if (setting.getVisibility() != null && !setting.getVisibility().displayable() || (settingRenderer = SettingRendererRegistry.getInstance().findRenderer(setting)) == null) continue;
                settingRenderer.onMouseRelease(mouseX, mouseY, button);
            }
        }
    }

    public Module getCurrentModule() {
        return this.currentModule;
    }

    public void setCurrentModule(Module module) {
        this.currentModule = module;
    }

    private int lerpColor(int fromColor, int toColor, float t) {
        float inv = 1.0f - t;
        int aFrom = fromColor >> 24 & 0xFF;
        int rFrom = fromColor >> 16 & 0xFF;
        int gFrom = fromColor >> 8 & 0xFF;
        int bFrom = fromColor & 0xFF;
        int aTo = toColor >> 24 & 0xFF;
        int rTo = toColor >> 16 & 0xFF;
        int gTo = toColor >> 8 & 0xFF;
        int bTo = toColor & 0xFF;
        int a = (int)((float)aFrom * inv + (float)aTo * t);
        int r = (int)((float)rFrom * inv + (float)rTo * t);
        int g = (int)((float)gFrom * inv + (float)gTo * t);
        int b = (int)((float)bFrom * inv + (float)bTo * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private void rescaleScroll(float scale) {
        float headerHeight;
        int contentHeight;
        float visibleHeight;
        if (this.lastScale <= 0.0f) {
            return;
        }
        float scaleRatio = scale / this.lastScale;
        this.scrollOffset *= scaleRatio;
        this.scrollTarget *= scaleRatio;
        if (this.currentModule != null) {
            this.calculateTotalHeight(this.currentModule, scale);
        }
        if (this.totalContentHeight > (visibleHeight = (float)(contentHeight = (int)(400.0f * scale) - (int)(40.0f * scale) - (int)(20.0f * scale)) - (headerHeight = 30.0f * scale))) {
            float maxScroll = this.totalContentHeight - visibleHeight;
            this.scrollOffset = Math.max(0.0f, Math.min(this.scrollOffset, maxScroll));
            this.scrollTarget = Math.max(0.0f, Math.min(this.scrollTarget, maxScroll));
        } else {
            this.scrollOffset = 0.0f;
            this.scrollTarget = 0.0f;
        }
    }
}
