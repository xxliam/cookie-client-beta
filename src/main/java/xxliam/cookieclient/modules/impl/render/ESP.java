package xxliam.cookieclient.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.utils.game.EntityUtil;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ProjectionUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * ESP：实体透视（Glow 发光 / Outlined 2D 描边框 + 血条）。
 * <p>
 * Glow 由 {@code MinecraftMixin} 调用 {@link #isGlowing} 实现；
 * 2D 框在本模块 {@link #render} 中投影绘制。
 */
public class ESP extends Module {

    public static ESP INSTANCE;

    private final ModeSetting modeSetting = new ModeSetting("Mode", "Glow", "Outlined 2D").withDefault("Outlined 2D");
    private final BooleanSetting skeletonSetting = new BooleanSetting("Skeleton", false);
    private final BooleanSetting playersSetting = new BooleanSetting("Players", true);
    private final BooleanSetting mobsSetting = new BooleanSetting("Mobs", false);
    private final BooleanSetting animalsSetting = new BooleanSetting("Animals", false);
    private final BooleanSetting itemsSetting = new BooleanSetting("Items", false);
    private final BooleanSetting arrowsSetting = new BooleanSetting("Arrows", true);
    private final BooleanSetting showHealthBarSetting = new BooleanSetting("Show Health Bar", true);
    private final ModeSetting healthBarPositionSetting = new ModeSetting("Health Bar Position", "Bottom", "Top", "Left", "Right").withDefault("Bottom");

    private final List<Vector2f> projectedPoints = new ArrayList<>();

    public ESP() {
        super("ESP", Category.RENDER);
        INSTANCE = this;
        addSetting(modeSetting);
        addSetting(skeletonSetting);
        addSetting(playersSetting);
        addSetting(mobsSetting);
        addSetting(animalsSetting);
        addSetting(itemsSetting);
        addSetting(arrowsSetting);
        addSetting(showHealthBarSetting);
        addSetting(healthBarPositionSetting);
    }

    /** Glow 模式：判断实体是否应发光（由 MinecraftMixin 调用）。 */
    public boolean isGlowing(Entity entity) {
        if (isEnabled() && "Glow".equalsIgnoreCase(modeSetting.getValue())) {
            if (entity instanceof Player && playersSetting.getValue()) return true;
            if (entity instanceof Animal && animalsSetting.getValue()) return true;
            if (entity instanceof Mob && mobsSetting.getValue()) return true;
            if (entity instanceof ItemEntity && itemsSetting.getValue()) return true;
            return entity instanceof Arrow && arrowsSetting.getValue();
        }
        return false;
    }

    private boolean shouldShowEntity(Entity entity) {
        if (entity == Minecraft.getInstance().player) return false;
        if (entity instanceof Player && playersSetting.getValue()) return true;
        if (entity instanceof Animal && animalsSetting.getValue()) return true;
        if (entity instanceof Mob && mobsSetting.getValue()) return true;
        return entity instanceof ItemEntity && itemsSetting.getValue();
    }

    private boolean isInRange(Entity entity) {
        return Minecraft.getInstance().player.distanceToSqr(entity) < 10000.0;
    }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!"Outlined 2D".equals(modeSetting.getValue())) return;

        List<Entity> visible = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (shouldShowEntity(entity) && isInRange(entity)) {
                visible.add(entity);
            }
        }
        if (visible.isEmpty()) return;

        Matrix4f matrix = guiGraphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Entity entity : visible) {
            AABB aabb = EntityUtil.getInterpolatedAABB(entity, partialTicks);
            Vec3[] corners = new Vec3[]{
                    new Vec3(aabb.minX, aabb.minY, aabb.minZ), new Vec3(aabb.maxX, aabb.minY, aabb.minZ),
                    new Vec3(aabb.maxX, aabb.minY, aabb.maxZ), new Vec3(aabb.minX, aabb.minY, aabb.maxZ),
                    new Vec3(aabb.minX, aabb.maxY, aabb.minZ), new Vec3(aabb.maxX, aabb.maxY, aabb.minZ),
                    new Vec3(aabb.maxX, aabb.maxY, aabb.maxZ), new Vec3(aabb.minX, aabb.maxY, aabb.maxZ)
            };
            projectedPoints.clear();
            boolean ok = true;
            for (Vec3 corner : corners) {
                Vector2f projected = ProjectionUtil.project(corner.x, corner.y, corner.z);
                if (projected == null) {
                    ok = false;
                    break;
                }
                projectedPoints.add(projected);
            }
            if (!ok || projectedPoints.isEmpty()) continue;

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Vector2f p : projectedPoints) {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
            }
            float pad = 3.0f;
            float x1 = minX - pad, y1 = minY - pad;
            float x2 = maxX + pad, y2 = maxY + pad;

            Color color = entity instanceof Player p ? ColorUtil.getPlayerColor(p) : Color.WHITE;
            drawFilledRect2D(builder, matrix, x1, y1, x2, y2, color);
            if (entity instanceof LivingEntity le && showHealthBarSetting.getValue()) {
                drawHealthBar(builder, matrix, le, x1, y1, x2, y2, color);
            }
        }

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }

    private void drawFilledRect2D(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float x2, float y2, Color color) {
        Renderer.drawQuad(builder, matrix, x1 - 1.0f, y1, x1 + 0.5f, y2 + 0.5f, Color.BLACK);
        Renderer.drawQuad(builder, matrix, x1 - 1.0f, y1 - 0.5f, x2 + 0.5f, y1 + 1.0f, Color.BLACK);
        Renderer.drawQuad(builder, matrix, x2 - 0.5f, y1, x2 + 0.5f, y2 + 0.5f, Color.BLACK);
        Renderer.drawQuad(builder, matrix, x1 - 1.0f, y2 - 0.5f, x2 + 0.5f, y2 + 0.5f, Color.BLACK);
        Renderer.drawQuad(builder, matrix, x1 - 0.5f, y1, x1, y2, color);
        Renderer.drawQuad(builder, matrix, x1, y2 - 0.5f, x2, y2, color);
        Renderer.drawQuad(builder, matrix, x1, y1, x2, y1 + 0.5f, color);
        Renderer.drawQuad(builder, matrix, x2 - 0.5f, y1, x2, y2, color);
    }

    private void drawHealthBar(BufferBuilder builder, Matrix4f matrix, LivingEntity entity,
                               float x, float y, float right, float bottom, Color color) {
        float healthFrac;
        if (entity instanceof Player p) {
            healthFrac = Mth.clamp(Math.min(p.getHealth(), p.getMaxHealth()) / p.getMaxHealth(), 0.0f, 1.0f);
        } else {
            healthFrac = Math.min(entity.getHealth() / entity.getMaxHealth(), 1.0f);
        }
        float width = right - x;
        float height = bottom - y;
        String position = healthBarPositionSetting.getValue();
        if (position == null) position = "Bottom";
        float barX, barY, barW, barH;
        switch (position) {
            case "Top" -> { barW = width; barH = 2.0f; barX = x; barY = y - 4.0f; }
            case "Left" -> { barW = 2.0f; barH = height; barX = x - 4.0f; barY = y; }
            case "Right" -> { barW = 2.0f; barH = height; barX = right + 2.0f; barY = y; }
            default -> { barW = width; barH = 2.0f; barX = x; barY = bottom + 2.0f; }
        }
        Renderer.drawQuad(builder, matrix, barX - 0.6f, barY - 0.6f, barX + barW + 0.6f, barY + barH + 0.6f, Color.BLACK);
        Renderer.drawQuad(builder, matrix, barX, barY, barX + barW, barY + barH, color.darker().darker());
        Color healthColor = getHealthColor(healthFrac);
        if ("Left".equals(position) || "Right".equals(position)) {
            Renderer.drawQuad(builder, matrix, barX, barY + barH * (1.0f - healthFrac), barX + barW, barY + barH, healthColor);
        } else {
            Renderer.drawQuad(builder, matrix, barX, barY, barX + barW * healthFrac, barY + barH, healthColor);
        }
    }

    private Color getHealthColor(float fraction) {
        return Color.getHSBColor(Math.max(0.0f, fraction) / 3.0f, 1.0f, 1.0f);
    }
}
