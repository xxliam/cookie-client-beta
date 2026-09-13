package xxliam.cookieclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.modules.impl.world.AutoPlay;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * AutoPlayHud：DynamicIsland 内的自动下一局倒计时（环形进度 + 对勾勾画动画）。
 * <p>
 * 照搬 OpenZen {@code shit.zen.hud.AutoPlayHud}：数据来自 {@link AutoPlay} 模块的
 * disconnectTime / pendingDisconnect / Delay 设置；环形进度用弹簧平滑逼近
 * elapsed/delay；倒计时结束(400ms)收缩高度并勾画对勾。布局（字号 poppinsRegular 24、
 * icon 28、padding 18、height 40/25）逐字保留，全部乘 {@link IslandMetrics#scale()}
 * 后<b>原生栅格化</b>。矢量部分由 {@link ZenHudDraw#drawArc} /
 * {@link ZenHudDraw#strokePolyline} 承担（等价 zen 的 drawArc / drawPath 描边）。
 */
public class AutoPlayHud implements IHudElement {

    /** 灵动岛整体缩放（zen 原始单位 → 目标单位）；由 Size 滑条驱动，每帧 refreshScale() 刷新。 */
    private static float S;

    static {
        refreshScale();
    }

    /** 按当前大小档位刷新缩放（字号在 font() 里即时取用）。 */
    private static void refreshScale() {
        S = IslandMetrics.scale();
    }

    private static final CustomFont font() {
        return FontStore.poppinsRegular(24.0f * S);
    }

    private static final String DONE_TEXT = "Done!";
    private static final String WAITING_TEXT = "Sending you to next game...";

    private float animProgress = 0.0f;
    private long lastUpdateTime = -1L;
    private long disableTime = -1L;

    @Override
    public boolean isVisible() {
        if (AutoPlay.instance == null || !AutoPlay.instance.isEnabled()) {
            return false;
        }
        if (AutoPlay.instance.pendingDisconnect) {
            return true;
        }
        long disconnectTime = AutoPlay.instance.disconnectTime;
        if (disconnectTime <= 0L) {
            return false;
        }
        double delayMs = AutoPlay.instance.getDelay().getValue().doubleValue() * 1000.0;
        long elapsed = System.currentTimeMillis() - disconnectTime;
        if ((double) elapsed >= delayMs) {
            long afterDoneMs = elapsed - (long) delayMs;
            return afterDoneMs < 500L;
        }
        return false;
    }

    @Override
    public Size size() {
        refreshScale();
        if (!isVisible()) {
            return new Size(0.0f, 40.0f * S);
        }
        long disconnectTime = AutoPlay.instance.disconnectTime;
        double delayMs = AutoPlay.instance.getDelay().getValue().doubleValue() * 1000.0;
        long elapsed = System.currentTimeMillis() - disconnectTime;
        boolean done = delayMs <= 0.0 || (double) elapsed >= delayMs;
        long afterDoneMs = done ? elapsed - (long) delayMs : -1L;
        String text = done ? DONE_TEXT : WAITING_TEXT;
        float textWidth = font().getStringWidth(text);
        float iconSize = 28.0f * S;
        float totalWidth = 18.0f * S + iconSize + 8.0f * S + textWidth + 18.0f * S;
        float width = totalWidth - 30.0f * S;
        width = Math.max(width, 60.0f * S);
        float height = 40.0f * S;
        if (done) {
            float t = Mth.clamp((float) afterDoneMs / 400.0f, 0.0f, 1.0f);
            height = Mth.lerp(t, 40.0f * S, 25.0f * S);
        }
        return new Size(width, height);
    }

    @Override
    public Alignment alignment() {
        return Alignment.CENTER;
    }

    @Override
    public boolean hasBackground() {
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, float x, float y, float width, float height, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || alpha <= 0.01f || AutoPlay.instance == null) {
            return;
        }
        refreshScale();
        long now = System.currentTimeMillis();
        if (!AutoPlay.instance.pendingDisconnect) {
            if (disableTime == -1L) {
                disableTime = now;
            }
        } else {
            disableTime = -1L;
        }
        if (lastUpdateTime == -1L) {
            lastUpdateTime = now;
        }
        long deltaTime = now - lastUpdateTime;
        lastUpdateTime = now;

        long disconnectTime = AutoPlay.instance.disconnectTime;
        double delaySec = AutoPlay.instance.getDelay().getValue().doubleValue();
        double delayMs = delaySec * 1000.0;
        long elapsed = System.currentTimeMillis() - disconnectTime;
        float targetProgress = delayMs > 0.0 ? (float) ((double) elapsed / delayMs) : 1.0f;
        targetProgress = Mth.clamp(targetProgress, 0.0f, 1.0f);
        float lerpT = Mth.clamp((float) deltaTime / 200.0f, 0.0f, 1.0f);
        animProgress = Mth.lerp(lerpT, animProgress, targetProgress);
        if (Math.abs(animProgress - targetProgress) < 0.01f) {
            animProgress = targetProgress;
        }

        boolean done = false;
        long afterDoneMs = 0L;
        if (disconnectTime > 0L) {
            long elapsed2 = System.currentTimeMillis() - disconnectTime;
            if (delayMs <= 0.0 || (double) elapsed2 >= delayMs) {
                done = true;
                afterDoneMs = delayMs > 0.0 ? elapsed2 - (long) delayMs : elapsed2;
            }
        }

        float centerY = y + height / 2.0f;
        float iconSize = height - 12.0f * S;
        float iconX = x + 18.0f * S;
        float iconY = y + 6.0f * S;
        float iconCx = iconX + iconSize / 2.0f;
        float iconCy = iconY + iconSize / 2.0f;
        float iconRadius = iconSize / 2.0f - 2.0f * S;

        // ---- 环形进度 ----
        int white = colorWithAlpha(Color.WHITE.getRGB(), alpha);
        if (animProgress > 0.001f) {
            float arcProgress = 360.0f * animProgress;
            ZenHudDraw.drawArc(guiGraphics.pose(), iconCx - iconRadius, iconCy - iconRadius,
                    iconCx + iconRadius, iconCy + iconRadius, -90.0f, arcProgress, 2.0f * S, white);
        }
        // ---- 对勾勾画 ----
        if (done) {
            float t = Mth.clamp((float) afterDoneMs / 400.0f, 0.0f, 1.0f);
            float p0x = iconCx - iconRadius * 0.4f;
            float p0y = iconCy;
            float midX = iconCx - iconRadius * 0.15f;
            float midY = iconCy + iconRadius * 0.3f;
            float endX = iconCx + iconRadius * 0.4f;
            float endY = iconCy - iconRadius * 0.3f;
            float seg1Len = (float) Math.hypot(midX - p0x, midY - p0y);
            float seg2Len = (float) Math.hypot(endX - midX, endY - midY);
            float totalLen = seg1Len + seg2Len;
            float drawLen = totalLen * t;
            List<float[]> points = new ArrayList<>();
            points.add(new float[]{p0x, p0y});
            if (drawLen <= seg1Len) {
                float seg1T = seg1Len > 0.0f ? drawLen / seg1Len : 0.0f;
                points.add(new float[]{Mth.lerp(seg1T, p0x, midX), Mth.lerp(seg1T, p0y, midY)});
            } else {
                points.add(new float[]{midX, midY});
                float seg2T = seg2Len > 0.0f ? (drawLen - seg1Len) / seg2Len : 0.0f;
                points.add(new float[]{Mth.lerp(seg2T, midX, endX), Mth.lerp(seg2T, midY, endY)});
            }
            ZenHudDraw.strokePolyline(guiGraphics.pose(), points, 2.0f * S, white);
        }

        // ---- 状态文字 ----
        String statusText = done ? DONE_TEXT : WAITING_TEXT;
        float statusX = iconX + iconSize + 8.0f * S;
        CustomFont font = font();
        float statusBaseline = centerY - ZenHudDraw.zenCapHeight(font) / 2.0f + 8.0f * S;
        ZenHudDraw.drawBaseline(guiGraphics.pose(), font, statusText, statusX, statusBaseline, white);
    }
}
