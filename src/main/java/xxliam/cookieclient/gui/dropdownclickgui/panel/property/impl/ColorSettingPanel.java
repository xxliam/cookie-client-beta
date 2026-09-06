package xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl;

import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.PropertyPanel;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.ColorSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;

import java.awt.Color;

/**
 * 颜色行（Opal {@code ColorPropertyComponent}）：右侧色块预览；右键展开 HSV 取色面板
 * （65×50 拾色器 + 8×50 色相条），按住拖动实时改色。cookie 的 ColorSetting 存 ARGB int，
 * 拖动只改 RGB、保留原 alpha。展开 125ms DECELERATE。
 */
public class ColorSettingPanel extends PropertyPanel {

    private static final float PICKER_W = 65.0f;
    private static final float PICKER_H = 50.0f;

    private final ColorSetting setting;

    private SmoothAnimationTimer expandAnim;
    private boolean expanded;
    private DragType dragType = DragType.NONE;

    public ColorSettingPanel(final ColorSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void init() {
        expandAnim = null;
        expanded = false;
        dragType = DragType.NONE;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        super.render(guiGraphics, mouseX, mouseY, delta, alpha);

        if (expandAnim == null) {
            expandAnim = new SmoothAnimationTimer();
            expandAnim.setCurrentValue(expanded ? 1.0 : 0.0);
        } else {
            expandAnim.animate(expanded ? 1.0 : 0.0, 0.125, Easings.EASE_OUT_QUAD);
            expandAnim.tick();
        }
        float anim = expandAnim.getValueF();

        label(guiGraphics, setting.getName(), x + 5.0f, y + 10.5f, alpha);

        int color = setting.getColor();
        int opaque = (color & 0x00FFFFFF) | 0xFF000000;
        Renderer.drawRoundedRect(guiGraphics.pose(), x + width - 22.0f, y + 3.5f, 18.0f, 10.0f, 3.0f,
                ColorUtil.withAlpha(color, alpha));

        if (anim <= 0.0f) {
            return;
        }

        float xPos = x + 5.0f;
        float yPos = y + DEFAULT_HEIGHT;
        float hueSatBright[] = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        float hue = hueSatBright[0];

        // 拖动实时更新（opal colorDragType 分支）
        if (mouseX != -1) {
            if (dragType == DragType.HUE) {
                float h = Math.min(1.0f, Math.max(0.0f, (mouseY - yPos) / PICKER_H));
                setHueSatBright(h, hueSatBright[1], hueSatBright[2], color);
            } else if (dragType == DragType.PICKER) {
                float s = Math.min(1.0f, Math.max(0.0f, (mouseX - xPos) / PICKER_W));
                float b = Math.min(1.0f, Math.max(0.0f, 1.0f - ((mouseY - yPos) / PICKER_H)));
                setHueSatBright(hueSatBright[0], s, b, color);
            }
        }
        // 值可能已变，重取 hue 供拾色器底色
        int current = setting.getColor();
        hueSatBright = Color.RGBtoHSB((current >> 16) & 0xFF, (current >> 8) & 0xFF, current & 0xFF, null);
        hue = hueSatBright[0];

        int rgba = (current & 0x00FFFFFF) | 0xFF000000;
        Renderer.pushScissor(Math.round(x), Math.round(y), Math.round(width), Math.round(height));

        // 拾色器底：纯色相 + 白(左→右消失) + 黑(上→下加深)
        Renderer.drawRect(guiGraphics.pose(), xPos, yPos, PICKER_W, PICKER_H,
                ColorUtil.withAlpha(Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000, alpha));
        Renderer.drawRoundedRectGradient(guiGraphics.pose(), xPos, yPos, PICKER_W, PICKER_H, 0.0f,
                ColorUtil.withAlpha(0xFFFFFFFF, alpha), ColorUtil.withAlpha(0xFFFFFFFF, 0.0f), 0.0f);
        Renderer.drawRoundedRectGradient(guiGraphics.pose(), xPos, yPos, PICKER_W, PICKER_H, 0.0f,
                ColorUtil.withAlpha(0xFF000000, 0.0f), ColorUtil.withAlpha(0xFF000000, alpha), 90.0f);

        // 当前指针位置圆环
        float px = xPos + hueSatBright[1] * PICKER_W;
        float py = yPos + (1.0f - hueSatBright[2]) * PICKER_H;
        Renderer.drawRoundedRect(guiGraphics.pose(), px - 2.0f, py - 2.0f, 4.0f, 4.0f, 2.0f,
                ColorUtil.withAlpha(-1, alpha));

        // 色相条（竖向 rainbow）
        float barX = xPos + PICKER_W + 5.0f;
        int segs = 18;
        float segH = PICKER_H / segs;
        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            int c0 = Color.HSBtoRGB(t0, 1.0f, 1.0f) | 0xFF000000;
            int c1 = Color.HSBtoRGB(t1, 1.0f, 1.0f) | 0xFF000000;
            Renderer.drawRoundedRectGradient(guiGraphics.pose(), barX, yPos + t0 * PICKER_H, 8.0f, segH + 0.5f, 0.0f,
                    ColorUtil.withAlpha(c0, alpha), ColorUtil.withAlpha(c1, alpha), 90.0f);
        }
        // 色相指针
        float hueY = yPos + hue * PICKER_H;
        Renderer.drawRoundedRect(guiGraphics.pose(), barX - 1.0f, hueY - 1.0f, 10.0f, 2.0f, 1.0f,
                ColorUtil.withAlpha(-1, alpha));

        setHeight(DEFAULT_HEIGHT + PICKER_H * anim);
        Renderer.popScissor();
    }

    private void setHueSatBright(float h, float s, float b, int oldArgb) {
        int rgb = Color.HSBtoRGB(h, Math.min(1f, Math.max(0f, s)), Math.min(1f, Math.max(0f, b)));
        int next = (oldArgb & 0xFF000000) | (rgb & 0x00FFFFFF);
        setting.setValue(next);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(x, y, width, DEFAULT_HEIGHT, mouseX, mouseY) && button == 1) {
            expanded = !expanded;
            return;
        }
        if (button != 0) {
            return;
        }
        float xPos = x + 5.0f;
        float yPos = y + DEFAULT_HEIGHT;
        if (isHovering(xPos, yPos, PICKER_W, PICKER_H, mouseX, mouseY)) {
            dragType = DragType.PICKER;
        } else if (isHovering(xPos + PICKER_W + 5.0f, yPos, 8.0f, PICKER_H, mouseX, mouseY)) {
            dragType = DragType.HUE;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragType = DragType.NONE;
    }

    private enum DragType {
        PICKER,
        HUE,
        NONE
    }
}
