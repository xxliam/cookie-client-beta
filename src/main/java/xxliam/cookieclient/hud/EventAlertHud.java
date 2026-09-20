package xxliam.cookieclient.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EventAlertHud：DynamicIsland 内的游戏事件告警（敌方末影珍珠预警 / 落雷告警）。
 * <p>
 * 照搬 OpenZen {@code shit.zen.hud.EventAlertHud}：
 * <ul>
 *   <li>珍珠预警：对飞行中的敌方珍珠做落点预测（逐 tick 推进、drag 0.99、重力 0.03、
 *       方块碰撞截止），取落点离自己最近的一颗。原先这段预测挂在 {@code Projectiles}
 *       模块上并暴露 {@code projectileMap} 供本 HUD 读取；该模块已被 Naven 版弹道轨迹渲染
 *       整体替换，预测遂内联到本类（算法与判定条件不变）；</li>
 *   <li>落雷在 5 秒窗口内记录、256 格内报警（EA0B 图标）。</li>
 * </ul>
 * 所有布局（padding 12、字号 materialicons 48/44 + poppins 8/6、图标基线偏移式、
 * 箭头角度/上下箭判定）逐字保留；zen 的闪电 Path 动画分支因图标字形路径不可用
 * （其 {@code getGlyphCodes} 返空导致 {@code iconPath==null}）恒走文字绘制分支，
 * cookie 侧同样直接绘制字形。
 * <p>
 * 尺寸：字号与布局常量均乘 {@link IslandMetrics#scale()} 后<b>原生栅格化</b>
 * （MaterialIcons 是符号字形，同样按目标字号栅格化）。
 */
public class EventAlertHud implements IHudElement {

    /** 灵动岛整体缩放（zen 原始单位 → 目标单位）；由 Size 滑条驱动，每帧 refreshScale() 刷新。 */
    private static float S;

    static {
        refreshScale();
    }

    /** 按当前大小档位刷新缩放（字号在 iconTitleFont/arrowFont/titleFont/timeFont 里即时取用）。 */
    private static void refreshScale() {
        S = IslandMetrics.scale();
    }

    /** 告警条目。 */
    public record AlertEntry(Vec3 position, double distance, Optional<Float> timeRemaining, String title, String icon) {
        /** 兼容 zen 的 pos() 访问器。 */
        public Vec3 pos() {
            return position;
        }

        public String getFormattedDescription() {
            if (timeRemaining.isPresent()) {
                return String.format("%.1fs \u00b7 %.1fm", timeRemaining.get(), distance);
            }
            return String.format("%.1fm", distance);
        }
    }

    private static final CustomFont iconTitleFont() {
        return FontStore.materialIcons(48.0f * S); // 图标字形（zen MaterialIcons 48）
    }

    private static final CustomFont arrowFont() {
        return FontStore.materialIcons(44.0f * S); // 箭头字形（zen MaterialIcons 44）
    }

    private static final CustomFont titleFont() {
        return FontStore.poppinsBold(8.0f * S);    // 告警标题（zen poppinsBold 8）
    }

    private static final CustomFont timeFont() {
        return FontStore.poppinsMedium(6.0f * S);  // 描述行（zen poppinsMedium 6）
    }

    private static final String PEARL_ICON = "\uE55E";
    private static final String LIGHTNING_ICON = "\uEA0B";
    private static final String ARROW_UP = "\uE5D8";
    private static final String ARROW_DOWN = "\uE5DB";

    private final Map<Vec3, Long> activeAlerts = new ConcurrentHashMap<>();
    private Vec3 lastAlertPos = null;
    private long lastAlertTime = 0L;

    /**
     * 敌方末影珍珠预警：在场飞行中的敌方珍珠里，取预测落点离自己最近的一颗。
     * <p>
     * 预测逻辑原在 {@code Projectiles} 模块（OpenZen 系数据源）；该模块已被 Naven 版
     * 弹道轨迹渲染替换，此处内联同样算法，筛选条件（存活 / 未落地 / 非自己投掷）不变。
     */
    private Optional<AlertEntry> findProjectileAlert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return Optional.empty();
        }
        AlertEntry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl pearl) || !entity.isAlive() || entity.onGround()) {
                continue;
            }
            Entity owner = pearl.getOwner();
            if (owner == null || owner.equals(mc.player)) {
                continue;
            }
            PearlPrediction prediction = predictPearlLanding(pearl);
            if (prediction == null) {
                continue;
            }
            double landingDistance = prediction.landing().distanceToSqr(mc.player.position());
            if (landingDistance < bestDistance) {
                bestDistance = landingDistance;
                best = new AlertEntry(prediction.landing(), prediction.distance(),
                        Optional.of((float) prediction.flightTime()), "Find an ender pearl!", PEARL_ICON);
            }
        }
        return Optional.ofNullable(best);
    }

    /** 珍珠落点预测结果：落点、剩余飞行时间（秒）、距自己距离（米）。 */
    private record PearlPrediction(Vec3 landing, double flightTime, double distance) {
    }

    /**
     * 敌方珍珠落点预测（原 {@code Projectiles.buildProjectileEntry}，算法逐字保留）：
     * 每步 1/20 秒推进，drag 0.99、重力 0.03，命中方块即返回该步终点/时间/距离。
     */
    private PearlPrediction predictPearlLanding(ThrownEnderpearl pearl) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        double x = pearl.getX();
        double y = pearl.getY();
        double z = pearl.getZ();
        double dx = pearl.getDeltaMovement().x;
        double dy = pearl.getDeltaMovement().y;
        double dz = pearl.getDeltaMovement().z;
        for (int step = 0; step < 1000; step++) {
            Vec3 start = new Vec3(x, y, z);
            Vec3 end = new Vec3(x + dx, y + dy, z + dz);
            HitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, pearl));
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                Vec3 hitLoc = hit.getLocation();
                return new PearlPrediction(hitLoc, step / 20.0, mc.player.getEyePosition().distanceTo(hitLoc));
            }
            x += dx;
            y += dy;
            z += dz;
            if (y < mc.level.getMinBuildHeight() - 10) {
                break;
            }
            dx *= 0.99;
            dy = dy * 0.99 - 0.03;
            dz *= 0.99;
        }
        return null;
    }

    private Optional<AlertEntry> findEntityAlert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        mc.level.entitiesForRendering().forEach(entity -> {
            if (entity instanceof LightningBolt && entity.isAlive()) {
                activeAlerts.put(entity.position(), now);
            }
        });
        activeAlerts.entrySet().removeIf(entry -> now - entry.getValue() > 5000L);
        if (activeAlerts.isEmpty()) {
            return Optional.empty();
        }
        return activeAlerts.keySet().stream()
                .filter(v -> mc.player.position().distanceToSqr(v) < 65536.0)
                .min(Comparator.comparingDouble(v -> mc.player.position().distanceToSqr(v)))
                .map(v -> new AlertEntry(v, mc.player.position().distanceTo(v), Optional.empty(), "Found a lightning strike!", LIGHTNING_ICON));
    }

    private Optional<AlertEntry> findBestAlert() {
        Optional<AlertEntry> pearl = findProjectileAlert();
        if (pearl.isPresent()) {
            return pearl;
        }
        return findEntityAlert();
    }

    @Override
    public boolean isVisible() {
        return findBestAlert().isPresent();
    }

    @Override
    public Size size() {
        refreshScale();
        return new Size(200.0f * S, 40.0f * S);
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
        if (mc == null || mc.player == null || alpha <= 0.01f) {
            return;
        }
        refreshScale();
        findBestAlert().ifPresent(alert -> {
            float padding = 12.0f * S;
            float iconX = x + padding;
            float centerY = y + height / 2.0f;
            boolean hasPathIcon = LIGHTNING_ICON.equals(alert.icon());
            if (hasPathIcon) {
                if (lastAlertPos == null || !lastAlertPos.equals(alert.pos())) {
                    lastAlertPos = alert.pos();
                    lastAlertTime = System.currentTimeMillis();
                }
            } else {
                lastAlertPos = null;
            }

            CustomFont iconTitleFont = iconTitleFont();
            CustomFont titleFont = titleFont();
            CustomFont timeFont = timeFont();
            CustomFont arrowFont = arrowFont();

            int white = ThemeHelper.foreground(alpha);
            float iconWidth = iconTitleFont.getStringWidth(alert.icon());
            // 图标 baseline（原式：centerY − (ascent+descent)/2 − descent，ascent 为 zen 负值）
            float iconBaseline = centerY - (ZenHudDraw.zenAscent(iconTitleFont) + ZenHudDraw.zenDescent(iconTitleFont)) / 2.0f
                    - ZenHudDraw.zenDescent(iconTitleFont);
            // zen iconPath 不可用（字形路径为空），恒走文字绘制分支
            ZenHudDraw.drawBaseline(guiGraphics.pose(), iconTitleFont, alert.icon(), iconX, iconBaseline, white);

            float textX = iconX + iconWidth + padding;
            float titleBaseline = centerY - ZenHudDraw.zenLineHeight(titleFont) / 2.0f + 2.0f * S;
            ZenHudDraw.drawBaseline(guiGraphics.pose(), titleFont, alert.title(), textX, titleBaseline, white);

            String desc = alert.getFormattedDescription();
            int grey = ThemeHelper.shade(0xAAAAAA, alpha);
            float descBaseline = centerY + ZenHudDraw.zenLineHeight(timeFont) / 2.0f + 6.0f * S;
            ZenHudDraw.drawBaseline(guiGraphics.pose(), timeFont, desc, textX, descBaseline, grey);

            // ---- 方向箭头 ----
            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 alertPos = alert.position();
            double dx = alertPos.x - eyePos.x;
            double dz = alertPos.z - eyePos.z;
            if (Math.sqrt(dx * dx + dz * dz) < 1.0) {
                String arrow = alertPos.y > eyePos.y ? ARROW_UP : ARROW_DOWN;
                float arrowWidth = arrowFont().getStringWidth(arrow);
                float arrowX = x + width - padding - arrowWidth;
                float arrowBaseline = centerY - (ZenHudDraw.zenAscent(arrowFont) + ZenHudDraw.zenDescent(arrowFont)) / 2.0f
                        - ZenHudDraw.zenDescent(arrowFont);
                ZenHudDraw.drawBaseline(guiGraphics.pose(), arrowFont, arrow, arrowX, arrowBaseline, white);
            } else {
                float arrowWidth = arrowFont.getStringWidth(ARROW_UP);
                float arrowX = x + width - padding - arrowWidth;
                float rotation = computeArrowRotation(dx, dz, mc.player.getYRot());
                PoseStack pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(arrowX + arrowWidth / 2.0f, centerY, 0.0f);
                pose.mulPose(Axis.ZP.rotationDegrees(rotation));
                float baseOffsetY = -(ZenHudDraw.zenAscent(arrowFont) + ZenHudDraw.zenDescent(arrowFont)) / 2.0f;
                ZenHudDraw.drawBaseline(pose, arrowFont, ARROW_UP, -arrowWidth / 2.0f, baseOffsetY, white);
                pose.popPose();
            }
        });
    }

    /** 原式：wrap(targetAngle(atan2(dz,dx) - 90°) − playerYaw)。 */
    private static float computeArrowRotation(double dx, double dz, float playerYaw) {
        float targetAngle = (float) (Mth.atan2(dz, dx) * 57.29577951308232) - 90.0f;
        float rotation = Mth.wrapDegrees(targetAngle - Mth.wrapDegrees(playerYaw));
        return rotation;
    }
}
