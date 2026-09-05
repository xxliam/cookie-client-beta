package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.gui.NewClickGui;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.ColorSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;

import java.util.Locale;

/**
 * 颜色设置控件：首行显示名称 + 当前色块 + HEX；左键点击展开 R/G/B/A 四条通道滑块。
 * <p>
 * 与 Theme 模块的 CUSTOM 主题配合：调整主/副色的 ARGB 值。
 * 高度 18（收起）↔ 66（展开，4 × 12 通道行），整体随展开动画平滑缩放。
 */
public class ColorSettingElement extends SettingElement<ColorSetting> {

    private static final float ROW_HEIGHT = 18.0f;
    private static final float SLIDER_ROW_HEIGHT = 12.0f;
    private static final float TRACK_X = 34.0f;
    private static final float TRACK_WIDTH = 72.0f;
    private static final float TRACK_HEIGHT = 4.0f;

    private final SmoothAnimationTimer expandTimer = new SmoothAnimationTimer();
    private final char[] channelNames = {'R', 'G', 'B', 'A'};
    private boolean expanded;
    private boolean isHovered;
    private boolean isTruncated;
    private int draggingChannel = -1;

    public ColorSettingElement(CategoryPanel parentPanel, ColorSetting setting) {
        super(parentPanel, setting);
    }

    private int color() {
        return setting.getColor();
    }

    private void setColor(int color) {
        setting.setValue(color);
    }

    @Override
    public float getHeight() {
        // 展开动画驱动整体高度（供模块面板实时重排）
        return ROW_HEIGHT + SLIDER_ROW_HEIGHT * 4.0f * expandTimer.getValueF();
    }

    @Override
    public float getAnimatedHeight() {
        return getHeight();
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        isHovered = CursorUtil.isInBounds(mouseX, mouseY, x, y, 120.0f, getHeight());
        visibilityTimer.animate(setting.getVisibility().displayable() ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        visibilityTimer.tick();
        expandTimer.animate(expanded ? 1.0 : 0.0, 0.25, Easings.EASE_OUT_POW2);
        expandTimer.tick();
        alpha *= visibilityTimer.getValueF();
        if (Mth.equal(alpha, 0.0f)) {
            return;
        }
        float expandAmount = expandTimer.getValueF();

        // 拖动通道滑块
        if (draggingChannel >= 0) {
            setChannelFromMouse(draggingChannel, mouseX);
        }

        // ---- 首行：名称 + 色块 + HEX ----
        String name = setting.getName();
        if (FontStore.AXIFORMA_REGULAR_14.getStringWidth(name) > 66.0f) {
            name = name.substring(0, Math.min(9, name.length())) + "...";
            isTruncated = true;
        }
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + 6.0f,
                y + (ROW_HEIGHT - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f, ColorUtil.withAlpha(-1, alpha * 0.8f));

        float hexWidth = FontStore.AXIFORMA_BOLD_13.getStringWidth(hex());
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, hex(), x + 120.0f - hexWidth - 6.0f - 18.0f,
                y + (ROW_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f, ColorUtil.withAlpha(-1, alpha * 0.85f));

        // 色块（不透明混合预览：RGB 覆盖在面板底色上按 alpha 混合）
        float swatchX = x + 120.0f - 18.0f - 5.0f;
        float swatchY = y + (ROW_HEIGHT - 12.0f) / 2.0f;
        Renderer.drawRoundedRect(poseStack, swatchX, swatchY, 12.0f, 12.0f, 2.0f, ColorUtil.withAlpha(blendOnBg(color()), alpha));

        // ---- 展开的通道滑块 ----
        if (expandAmount > 0.01f) {
            for (int channel = 0; channel < 4; channel++) {
                float rowY = y + ROW_HEIGHT + channel * SLIDER_ROW_HEIGHT;
                int channelValue = channelValue(channel);
                float ratio = channelValue / 255.0f;

                FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, String.valueOf(channelNames[channel]),
                        x + 8.0f, rowY + (SLIDER_ROW_HEIGHT - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f,
                        ColorUtil.withAlpha(channel == 3 ? 0xFFAAAAAA : channelAccent(channel), alpha * 0.9f));

                float trackY = rowY + (SLIDER_ROW_HEIGHT - TRACK_HEIGHT) / 2.0f;
                Renderer.drawRoundedRect(poseStack, x + TRACK_X, trackY, TRACK_WIDTH, TRACK_HEIGHT, 2.0f,
                        ColorUtil.withAlpha(ColorUtil.fromRGB(45, 45, 45), alpha));
                Renderer.drawRoundedRect(poseStack, x + TRACK_X, trackY, TRACK_WIDTH * ratio, TRACK_HEIGHT, 2.0f,
                        ColorUtil.withAlpha(channel == 3 ? 0xFFAAAAAA : channelAccent(channel), alpha * (0.4f + 0.6f * ratio)));
                float knobX = x + TRACK_X + TRACK_WIDTH * ratio - 3.0f;
                Renderer.drawRoundedRect(poseStack, knobX, trackY - 1.5f, 6.0f, 7.0f, 3.0f, ColorUtil.withAlpha(-1, alpha));
            }
        }

        if (isHovered && isTruncated) {
            parentPanel.setHoveredSettingElement(this);
            parentPanel.setTooltipText(setting.getName());
            parentPanel.setShowTooltip(true);
        } else if (parentPanel.getHoveredSettingElement() == this) {
            parentPanel.setShowTooltip(false);
            parentPanel.setHoveredSettingElement(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.getVisibility().displayable() || button != 0) {
            return false;
        }
        if (!CursorUtil.isInBounds((float) mouseX, (float) mouseY, x, y, 120.0f, getHeight())) {
            return false;
        }
        // 首行：切换展开
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, x, y, 120.0f, ROW_HEIGHT)) {
            expanded = !expanded;
            return true;
        }
        // 通道行：按下即跳转 + 拖动
        if (expanded) {
            for (int channel = 0; channel < 4; channel++) {
                float rowY = y + ROW_HEIGHT + channel * SLIDER_ROW_HEIGHT;
                if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, x + TRACK_X - 3.0f, rowY, TRACK_WIDTH + 6.0f, SLIDER_ROW_HEIGHT)) {
                    draggingChannel = channel;
                    setChannelFromMouse(channel, (int) mouseX);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingChannel = -1;
        return false;
    }

    private void setChannelFromMouse(int channel, int mouseX) {
        float ratio = Mth.clamp(((float) mouseX - (x + TRACK_X)) / TRACK_WIDTH, 0.0f, 1.0f);
        int value = Math.round(ratio * 255.0f);
        int c = color();
        int a = (c >>> 24) & 0xFF;
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        setColor(ColorUtil.fromARGB(
                channel == 0 ? value : r,
                channel == 1 ? value : g,
                channel == 2 ? value : b,
                channel == 3 ? value : a));
    }

    private int channelValue(int channel) {
        int c = color();
        return switch (channel) {
            case 0 -> (c >> 16) & 0xFF;
            case 1 -> (c >> 8) & 0xFF;
            case 2 -> c & 0xFF;
            default -> (c >>> 24) & 0xFF;
        };
    }

    private static int channelAccent(int channel) {
        return switch (channel) {
            case 0 -> 0xFFE05252;
            case 1 -> 0xFF4CAF50;
            case 2 -> 0xFF3F8EFC;
            default -> 0xFFAAAAAA;
        };
    }

    private String hex() {
        int c = color();
        return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
    }

    /** 把当前 ARGB 混到面板底色 (23,23,23) 上，得到不透明预览色。 */
    private static int blendOnBg(int color) {
        int a = (color >>> 24) & 0xFF;
        if (a >= 255) {
            return color | 0xFF000000;
        }
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float t = a / 255.0f;
        int br = 23, bg = 23, bb = 23;
        return 0xFF000000
                | ((Math.round(r * t + br * (1.0f - t)) & 0xFF) << 16)
                | ((Math.round(g * t + bg * (1.0f - t)) & 0xFF) << 8)
                | (Math.round(b * t + bb * (1.0f - t)) & 0xFF);
    }
}
