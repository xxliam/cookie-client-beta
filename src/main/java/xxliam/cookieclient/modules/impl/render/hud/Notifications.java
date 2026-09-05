package xxliam.cookieclient.modules.impl.render.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.notification.Notification;
import xxliam.cookieclient.notification.NotificationType;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications：屏幕右下角的通知队列渲染（对应 opal {@code NotificationsElement}）。
 * <p>
 * 布局参数照搬 opal：padding=3、卡高 21、图标 14、圆角 4、背景 {@code 0x80090909}、
 * 图标底片圆角 2.75 且用 darker(icon,0.6) 的 50% 透明、标题 7px(bold)、描述 6.5px(medium, 0xFFAAAAAA)、
 * x 滑入/滑出 400ms EASE_OUT_EXPO。
 * 通知队列由 {@link CookieClient#NOTIFICATION_MANAGER} 维护。
 */
public class Notifications extends Module {

    public static Notifications INSTANCE;

    /** 模块开关时是否弹出通知（opal NotificationSettings「On module toggle」默认 false）。 */
    public final BooleanSetting toggleNotifications = new BooleanSetting("On module toggle", false);

    private static final float PADDING = 3.0f;
    private static final float HEIGHT = 21.0f;
    private static final float ICON_SIZE = 14.0f;
    private static final float ICON_OFFSET = ICON_SIZE + PADDING;

    private final Map<Notification, SmoothAnimationTimer> animations = new HashMap<>();

    public Notifications() {
        super("Notifications", Category.RENDER);
        INSTANCE = this;
        setVisible(false); // HUD 基础设施，不进 ModuleList 列表
        addSetting(toggleNotifications);
    }

    /** 模块被切换时调用（照搬 opal 的模块开关通知；HUD 基础设施不通知）。 */
    public static void onModuleToggled(Module module, boolean enabled) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.toggleNotifications.getValue()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || CookieClient.NOTIFICATION_MANAGER == null || !module.isVisible()) {
            return;
        }
        CookieClient.NOTIFICATION_MANAGER.publish(
                enabled ? NotificationType.SUCCESS : NotificationType.ERROR,
                module.getName(),
                enabled ? "enabled" : "disabled",
                2000);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }
        if (CookieClient.NOTIFICATION_MANAGER == null) {
            return;
        }
        List<Notification> notifications = CookieClient.NOTIFICATION_MANAGER.getNotifications();
        if (notifications.isEmpty()) {
            return;
        }

        // 快照遍历，便于安全移除
        List<Notification> snapshot = new ArrayList<>(notifications);
        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        int scaledHeight = mc.getWindow().getGuiScaledHeight();

        for (int i = 0; i < snapshot.size(); i++) {
            Notification notification = snapshot.get(i);
            SmoothAnimationTimer animation = animations.computeIfAbsent(notification,
                    k -> {
                        SmoothAnimationTimer timer = new SmoothAnimationTimer();
                        timer.setCurrentValue(scaledWidth); // 初始位于屏外右缘
                        return timer;
                    });

            float titleWidth = FontStore.PRODUCTSANS_BOLD_7.getStringWidth(notification.getTitle());
            float descWidth = FontStore.PRODUCTSANS_MEDIUM_7_5.getStringWidth(notification.getDescription());
            float width = Math.max(100.0f, ICON_OFFSET + Math.max(titleWidth, descWidth));
            float endX = scaledWidth - width - PADDING;

            animation.animate(notification.hasExpired() ? scaledWidth : endX, 0.4, Easings.EASE_OUT_EXPO);
            animation.tick();

            float x = animation.getValueF();
            float y = scaledHeight - (PADDING * 2) - ((i + 1) * (HEIGHT + PADDING));

            int iconColor = notification.getType().getIconColor();

            // 卡背景
            Renderer.drawRoundedRect(guiGraphics.pose(), x, y, width, HEIGHT, 4.0f, 0x80090909);

            // 图标底片（opal：iconColor 变暗 60% 后 50% 透明，圆角 2.75）
            Renderer.drawRoundedRect(guiGraphics.pose(), x + PADDING - 0.5f, y + PADDING / 2.0f + 0.5f,
                    ICON_OFFSET, ICON_OFFSET, 2.75f,
                    ColorUtil.applyOpacity(ColorUtil.darker(iconColor, 0.6f), 0.5f));

            // 图标：按字形视觉墨迹在图标底片内居中（drawString 的 y 是格顶，需换算）
            drawIconCentered(guiGraphics, FontStore.MATERIALICONS_14, notification.getType().getIcon(), iconColor,
                    x + PADDING - 0.5f + ICON_OFFSET / 2.0f,
                    y + PADDING / 2.0f + 0.5f + ICON_OFFSET / 2.0f);

            // 标题 / 描述（基线换算：drawString 用顶坐标，故减去各自 ascent）
            drawText(guiGraphics, FontStore.PRODUCTSANS_BOLD_7, notification.getTitle(),
                    x + (PADDING * 2) + ICON_OFFSET, y + (PADDING * 3), 0xFFFFFFFF);
            drawText(guiGraphics, FontStore.PRODUCTSANS_MEDIUM_6_5, notification.getDescription(),
                    x + (PADDING * 2) + ICON_OFFSET, y + (PADDING * 3) + 7.5f, 0xFFAAAAAA);

            // 过期且完全滑出屏外后移除（opal：hasExpired && animation 到达 scaledWidth）
            if (notification.hasExpired() && animation.getValueF() >= scaledWidth - 0.5f) {
                CookieClient.NOTIFICATION_MANAGER.remove(notification);
                animations.remove(notification);
            }
        }
    }

    /** 按 opal 基线语义绘制（baselineY 对应 opal drawString 的 y 参数）。 */
    private static void drawText(GuiGraphics guiGraphics, CustomFont font, String text, float x, float baselineY, int color) {
        float top = baselineY - font.getFontMetrics().getAscent() / (float) font.getScale();
        font.drawString(guiGraphics.pose(), text, x, top, color);
    }

    /**
     * 单字符图标按「视觉墨迹」居中于 (centerX, centerY)。
     * <p>
     * drawString 的 y 是字形格顶（内部先 translate --y），格顶 + ascent = baseline；
     * 墨迹中心 = baseline + ink.y + ink.h/2，反解出 drawString 的入参坐标。
     */
    private static void drawIconCentered(GuiGraphics guiGraphics, CustomFont font, String icon, int color,
                                         float centerX, float centerY) {
        if (icon == null || icon.isEmpty()) {
            return;
        }
        CustomFont.GlyphVisualBounds ink = font.getGlyphVisualBounds(icon.charAt(0));
        float asc = font.getFontMetrics().getAscent() / (float) font.getScale();
        if (ink.width() <= 0.0f || ink.height() <= 0.0f) {
            // 兜底：按 em 盒中线近似居中
            font.drawString(guiGraphics.pose(), icon,
                    centerX - font.getStringWidth(icon) / 2.0f,
                    centerY + 1.0f - asc / 2.0f, color);
            return;
        }
        font.drawString(guiGraphics.pose(), icon,
                centerX - ink.x() - ink.width() / 2.0f,
                centerY + 1.0f - asc - ink.y() - ink.height() / 2.0f, color);
    }
}
