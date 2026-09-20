package xxliam.cookieclient.modules.impl.render.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.notification.Notification;
import xxliam.cookieclient.notification.NotificationSounds;
import xxliam.cookieclient.notification.NotificationType;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications：屏幕右下角的通知队列渲染，两档风格：
 * <ul>
 *   <li>{@code Opal}：复刻 opal {@code NotificationsElement} —— padding=3、卡高 21、
 *       图标 14、圆角 4、背景 {@code 0x80090909}、图标底片圆角 2.75 且用
 *       darker(icon,0.6) 的 50% 透明、标题 7px(bold)、描述 6.5px(medium, 0xFFAAAAAA)、
 *       x 滑入/滑出 400ms EASE_OUT_EXPO；</li>
 *   <li>{@code Naven}：复刻 Naven 通知队列 —— 从右缘按宽度展开、自下而上堆叠的
 *       单行圆角色条（无图标、无标题/描述分行，文案 = 标题 + 空格 + 描述）。</li>
 * </ul>
 * 通知队列由 {@link CookieClient#NOTIFICATION_MANAGER} 维护，两种风格共用同一队列。
 */
public class Notifications extends Module {

    public static Notifications INSTANCE;

    /** 模块开关时是否弹出通知（opal NotificationSettings「On module toggle」默认 false）。 */
    public final BooleanSetting toggleNotifications = new BooleanSetting("On module toggle", false);

    /** 开关通知是否附带提示音：模块开启播 on / 关闭播 off（用户可选是否开启音效）。 */
    public final BooleanSetting sound = new BooleanSetting("Sound", true);

    /** 风格档：Opal = 复刻 opal；Naven = 复刻 Naven 通知队列。 */
    private final ModeSetting mode = new ModeSetting("Mode", "Opal", "Naven").withDefault("Opal");

    private static final float PADDING = 3.0f;
    private static final float HEIGHT = 21.0f;
    private static final float ICON_SIZE = 14.0f;
    private static final float ICON_OFFSET = ICON_SIZE + PADDING;

    // ---- Naven 档参数（照搬 Naven Notification / NotificationManager） ----
    /** 每行占位高度（{@code Notification.getHeight}）。 */
    private static final float NAVEN_ROW_HEIGHT = 24.0f;
    /** 卡片实际高度与圆角（Naven：20 高、圆角 5）。 */
    private static final float NAVEN_CARD_HEIGHT = 20.0f;
    private static final float NAVEN_RADIUS = 5.0f;
    /** 底部起始留白（Naven {@code height = 5.0F}）与卡片宽度冗余（{@code 文本宽 + 12}）。 */
    private static final float NAVEN_BASE_HEIGHT = 5.0f;
    private static final float NAVEN_WIDTH_EXTRA = 12.0f;
    /** 视觉字号（Naven 固定 0.35 × 20 = 7，与 opal 档标题字号一致）。 */
    private static final float NAVEN_VISUAL_SIZE = 7.0f;

    private final Map<Notification, SmoothAnimationTimer> animations = new HashMap<>();

    /** Naven 档的宽/高动画（对应 Naven 的 widthTimer / heightTimer）。 */
    private static final class NavenAnimation {
        final SmoothAnimationTimer width = new SmoothAnimationTimer();
        final SmoothAnimationTimer height = new SmoothAnimationTimer();
    }

    private final Map<Notification, NavenAnimation> navenAnimations = new HashMap<>();

    public Notifications() {
        super("Notifications", Category.RENDER);
        INSTANCE = this;
        setVisible(false); // HUD 基础设施，不进 ModuleList 列表
        addSetting(mode);
        addSetting(toggleNotifications);
        addSetting(sound);
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
        if (INSTANCE.sound.getValue()) {
            NotificationSounds.play(enabled);
        }
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
        if (mode.is("Naven")) {
            renderNaven(guiGraphics, mc);
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

    /**
     * Naven 档通知渲染（照搬 Naven {@code NotificationManager.onRender} + {@code Notification.render}）。
     * <p>
     * 自下而上堆叠：起始 {@code height = 5}，每行先 {@code height += 24}（Naven
     * {@code getHeight}），行坐标 {@code y = guiHeight - heightTimer}；
     * x 由宽度动画从右缘展开（{@code x = guiWidth - widthTimer + 2}）。
     * 卡片 = 圆角 5、高 20 的纯色块（落点 {@code (x+2, y+4)}，宽 = 文本宽 + 12），
     * 白字落点 {@code (x+6, y+9)}；过期后宽/高动画归零并移出队列。
     * <p>
     * 与 Naven 的差异：①卡片色跟随 {@link ThemeHelper} 主题主色（Naven 按
     * SUCCESS/INFO/WARNING/ERROR 用四种实心色，cookie 的通知类型色见
     * {@code NotificationType#getIconColor()}，如需恢复语义色改此处即可）；
     * ②文案取「标题 + 空格 + 描述」（cookie 的通知是标题/描述两段式，Naven 只有一句）；
     * ③字体用 cookie 的 Poppins（Naven 用 HarmonyOS Sans，cookie 无此字）。
     */
    private void renderNaven(GuiGraphics guiGraphics, Minecraft mc) {
        List<Notification> notifications = CookieClient.NOTIFICATION_MANAGER.getNotifications();
        if (notifications.isEmpty()) {
            return;
        }
        List<Notification> snapshot = new ArrayList<>(notifications);
        CustomFont font = FontStore.poppinsMedium(NAVEN_VISUAL_SIZE);
        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        int cardColor = ThemeHelper.getThemeColors()[0];
        PoseStack pose = guiGraphics.pose();

        float height = NAVEN_BASE_HEIGHT;
        for (Notification notification : snapshot) {
            String message = notification.getTitle() + " " + notification.getDescription();
            float width = font.getStringWidth(message) + NAVEN_WIDTH_EXTRA;
            height += NAVEN_ROW_HEIGHT;

            NavenAnimation animation = navenAnimations.computeIfAbsent(notification, k -> new NavenAnimation());
            boolean expired = notification.hasExpired();
            animation.width.animate(expired ? 0.0d : width, 0.4, Easings.EASE_OUT_EXPO);
            animation.height.animate(expired ? 0.0d : height, 0.4, Easings.EASE_OUT_EXPO);
            animation.width.tick();
            animation.height.tick();

            float x = (float) scaledWidth - animation.width.getValueF() + 2.0f;
            float y = (float) scaledHeight - animation.height.getValueF();

            Renderer.drawRoundedRect(pose, x + 2.0f, y + 4.0f, width, NAVEN_CARD_HEIGHT, NAVEN_RADIUS, cardColor);
            font.drawStringRGB(pose, message, x + 6.0f, y + 9.0f, 1.0f, 1.0f, 1.0f, 1.0f);

            // 过期且展开动画收回完毕后移出队列（对应 Naven：widthTimer 动画结束即移除）
            if (expired && !animation.width.isAnimating()) {
                CookieClient.NOTIFICATION_MANAGER.remove(notification);
                navenAnimations.remove(notification);
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
