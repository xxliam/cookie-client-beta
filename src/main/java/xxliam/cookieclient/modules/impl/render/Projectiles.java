package xxliam.cookieclient.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.modules.impl.render.projectiles.BasicProjectileData;
import xxliam.cookieclient.modules.impl.render.projectiles.EntityArrowData;
import xxliam.cookieclient.modules.impl.render.projectiles.EntityPotionData;
import xxliam.cookieclient.modules.impl.render.projectiles.ProjectileData;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.utils.game.RayTraceUtil;
import xxliam.cookieclient.utils.render.WorldRenderHelper;
import xxliam.cookieclient.utils.rotation.RotationHandler;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Projectiles：弹道轨迹渲染。
 * <p>
 * 由 Naven-Modern {@code obsoverlay.modules.impl.render.Projectile} 完整替换掉此前的
 * OpenZen 系「珍珠落点数据源」实现（那份数据源已内联回其唯一消费者 {@code EventAlertHud}）。
 * 两部分：
 * <ul>
 *   <li><b>手持投掷物预测轨迹</b>（Naven {@code onRender}，由 {@code Show Trajectory} 开关控制）：
 *       主手为可投掷物时，按物品类型
 *       取初速系数（弓/弩 1.0、其它 0.4）、弓力公式（{@code (7200-tick)/20} 再平方补正 ×3）、
 *       逐物品重力表（0.05 / 0.4 / 0.15 / 0.015 / 0.03），逐 1/20 秒推进最多 1000 步，
 *       每步 {@code drag×0.99}、方块与实体命中即截止；落点画实心盒 + 描边盒，
 *       命中方块按面朝向着色（顶面绿）、命中实体红光高亮实体盒。静默旋转激活时
 *       （{@link RotationHandler#isRotating}）用目标旋转预测，否则用玩家视角插值。</li>
 *   <li><b>在飞弹体拖尾</b>（Naven {@code onRender3D}）：对箭/药水/珍珠/蛋/雪球按数据表
 *       （颜色 + 碰撞盒尺寸 + 重力）预测后续轨迹并画渐变折线，由 5 个布尔设置分别开关。</li>
 * </ul>
 * 坐标约定：顶点一律相机相对量（渲染通道的 PoseStack 只含相机旋转），与 Naven 一致。
 */
public class Projectiles extends Module {

    public static Projectiles INSTANCE;

    // ---- 弹体数据表（照搬 Naven 的颜色 / 碰撞盒 / 重力） ----
    private final EntityArrowData arrowsColor = new EntityArrowData();
    private final EntityPotionData potionsColor = new EntityPotionData();
    private final BasicProjectileData enderPearlColor =
            new BasicProjectileData(Collections.singleton(ThrownEnderpearl.class), new Color(173, 12, 255));
    private final BasicProjectileData eggColor =
            new BasicProjectileData(Collections.singleton(ThrownEgg.class), new Color(255, 238, 154));
    private final BasicProjectileData snowballColor =
            new BasicProjectileData(Collections.singleton(Snowball.class), new Color(255, 255, 255));

    public final BooleanSetting showTrajectory = new BooleanSetting("Show Trajectory", true);
    public final BooleanSetting showArrows = new BooleanSetting("Show Arrows", true);
    public final BooleanSetting showPearls = new BooleanSetting("Show Pearls", true);
    public final BooleanSetting showPotions = new BooleanSetting("Show Potions", false);
    public final BooleanSetting showEggs = new BooleanSetting("Show Eggs", false);
    public final BooleanSetting showSnowballs = new BooleanSetting("Show Snowballs", false);

    public Projectiles() {
        super("Projectiles", Category.RENDER);
        INSTANCE = this;
        addSetting(showTrajectory);
        addSetting(showArrows);
        addSetting(showPearls);
        addSetting(showPotions);
        addSetting(showEggs);
        addSetting(showSnowballs);
    }

    @Override
    public void renderWorld(PoseStack poseStack, Camera camera, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 cameraPos = camera.getPosition();
        this.renderProjectileTraces(poseStack, cameraPos);
        this.renderTrajectoryPreview(poseStack, cameraPos, partialTicks);
    }

    // ---------------------------------------------------------------
    // 在飞弹体拖尾（Naven Projectile.onRender3D）
    // ---------------------------------------------------------------

    private void renderProjectileTraces(PoseStack stack, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.projectile.Projectile) {
                ProjectileData data = this.getProjectileDataByEntity(entity);
                if (data != null) {
                    stack.pushPose();
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.setShader(GameRenderer::getPositionShader);
                    Color color = data.getColor(entity);
                    RenderSystem.setShaderColor(color.getRed() / 255.0F, color.getGreen() / 255.0F,
                            color.getBlue() / 255.0F, 1.0F);
                    this.renderTrace(stack, entity, data, cameraPos);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.disableBlend();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                    stack.popPose();
                }
            }
        }
    }

    /** 按实体类型查数据表；已落地或本刻未水平移动的弹体不画（照搬 Naven）。 */
    private ProjectileData getProjectileDataByEntity(Entity entity) {
        if (entity.onGround()) {
            return null;
        }
        if (entity.getX() == entity.xOld && entity.getZ() == entity.zOld) {
            return null;
        }
        for (ProjectileData data : this.getProjectileInfos()) {
            if (data.isTargetEntity(entity)) {
                return data;
            }
        }
        return null;
    }

    /** 当前开启的弹体数据表（对应 Naven 的 5 个 Show 开关）。 */
    private List<ProjectileData> getProjectileInfos() {
        List<ProjectileData> infos = new ArrayList<>();
        if (this.showArrows.getValue()) {
            infos.add(this.arrowsColor);
        }
        if (this.showPotions.getValue()) {
            infos.add(this.potionsColor);
        }
        if (this.showPearls.getValue()) {
            infos.add(this.enderPearlColor);
        }
        if (this.showEggs.getValue()) {
            infos.add(this.eggColor);
        }
        if (this.showSnowballs.getValue()) {
            infos.add(this.snowballColor);
        }
        return infos;
    }

    /**
     * 弹体后续轨迹折线（照搬 Naven {@code Projectile.render}）。
     * <p>
     * 用弹体当前速度与数据表的重力/碰撞盒逐步推进，遇方块或可碰撞生物即把终点吸附到命中点。
     */
    private void renderTrace(PoseStack matrix, Entity entity, ProjectileData projectileInfo, Vec3 cameraPos) {
        if (entity == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer thePlayer = mc.player;
        ClientLevel theWorld = mc.level;
        Color color = projectileInfo.getColor(entity);
        if (color == null) {
            color = new Color(255, 255, 255);
        }
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        double posX = entity.getX();
        double posY = entity.getY();
        double posZ = entity.getZ();
        double motionX = entity.getDeltaMovement().x;
        double motionY = entity.getDeltaMovement().y;
        double motionZ = entity.getDeltaMovement().z;
        this.drawTraceVertex(color, builder, matrix, posX, posY, posZ, cameraPos);

        while (true) {
            float data1 = projectileInfo.getData1();
            float data2 = projectileInfo.getData2();
            AABB aabb = new AABB(posX - data1, posY, posZ - data1, posX + data1, posY + data2, posZ + data1);
            Vec3 vec3 = new Vec3(posX, posY, posZ);
            Vec3 vec3WithMotion = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            HitResult movingObj = RayTraceUtil.clipWithEntity(vec3, vec3WithMotion, false,
                    entity instanceof Arrow, false, entity);
            if (!movingObj.getType().equals(HitResult.Type.MISS)) {
                vec3WithMotion = new Vec3(movingObj.getLocation().x, movingObj.getLocation().y,
                        movingObj.getLocation().z);
            }

            List<Entity> nearbyEntities = theWorld.getEntities(thePlayer,
                    aabb.contract(motionX, motionY, motionZ).expandTowards(1.0, 1.0, 1.0));
            double lastMinDistance = 0.0;
            for (Entity nearby : nearbyEntities) {
                if (nearby instanceof LivingEntity && !(nearby instanceof EnderMan)
                        && nearby.canBeCollidedWith() && !nearby.equals(thePlayer)) {
                    aabb = nearby.getBoundingBox().expandTowards(0.3, 0.3, 0.3);
                    EntityHitResult aabbMovingObj = RayTraceUtil.getEntityHit(aabb, vec3, vec3WithMotion);
                    if (aabbMovingObj != null) {
                        double distance = vec3.distanceTo(aabbMovingObj.getLocation());
                        if (distance < lastMinDistance || lastMinDistance == 0.0) {
                            lastMinDistance = distance;
                            movingObj = aabbMovingObj;
                        }
                    }
                }
            }

            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            if (!movingObj.getType().equals(HitResult.Type.MISS)) {
                posX = movingObj.getLocation().x;
                posY = movingObj.getLocation().y;
                posZ = movingObj.getLocation().z;
                break;
            }
            if (posY < -128.0) {
                break;
            }
            double drag = entity.isInWater() ? 0.8 : 0.99;
            motionX *= drag;
            double nextMotionY = motionY * drag;
            motionZ *= drag;
            motionY = nextMotionY - projectileInfo.getGravity();
            this.drawTraceVertex(color, builder, matrix, posX + motionX, posY + motionY, posZ + motionZ, cameraPos);
        }
        tesselator.end();
    }

    /**
     * 拖尾顶点（照搬 Naven {@code Projectile.drawVertex} 的相机相对换算）。
     * <p>
     * 差异：Naven 减的是「相机实体插值位置」再额外把 Y 减 1.5（近似眼高），
     * 这里直接用真实相机位置，避免第三人称/骑乘时整体偏移。
     */
    private void drawTraceVertex(Color color, BufferBuilder builder, PoseStack stack,
                                 double x, double y, double z, Vec3 cameraPos) {
        builder.vertex(stack.last().pose(),
                        (float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z))
                .color(color.getRGB())
                .endVertex();
    }

    // ---------------------------------------------------------------
    // 手持投掷物预测轨迹（Naven Projectile.onRender）
    // ---------------------------------------------------------------

    private void renderTrajectoryPreview(PoseStack stack, Vec3 cameraPos, float partialTicks) {
        if (!this.showTrajectory.getValue()) {
            return;
        }
        Path pathResult = this.getPath(partialTicks);
        if (pathResult == null) {
            return;
        }
        List<Vec3> path = pathResult.path();
        if (path.size() < 2) {
            return;
        }
        stack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        this.drawLine(stack, path, cameraPos);
        Vec3 end = path.get(path.size() - 1);
        this.drawEndOfLine(stack, end, cameraPos, pathResult.result(), partialTicks);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        stack.popPose();
    }

    /**
     * 轨迹主线。
     * <p>
     * 差异：Naven 用 {@code path.get(0)}（玩家眼位附近）当相机原点，第三人称会整体偏移；
     * 这里统一改用真实相机位置。
     */
    private void drawLine(PoseStack matrixStack, List<Vec3> path, Vec3 cameraPos) {
        Matrix4f matrix = matrixStack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        bufferBuilder.begin(Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        for (Vec3 point : path) {
            bufferBuilder.vertex(matrix,
                    (float) (point.x - cameraPos.x), (float) (point.y - cameraPos.y), (float) (point.z - cameraPos.z))
                    .endVertex();
        }
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    /**
     * 轨迹终点标记（照搬 Naven {@code Projectile.drawEndOfLine}）。
     * <p>
     * 命中方块时按命中面摆一个贴面的小盒（顶面染绿），命中实体时把该实体盒染红并做半步回溯插值；
     * 再在终点画 0.25 透明度的实心盒 + 0.75 透明度的描边盒。
     */
    private void drawEndOfLine(PoseStack matrixStack, Vec3 end, Vec3 cameraPos, HitResult result, float partialTicks) {
        AABB bb = new AABB(0.15, 0.15, 0.15, 0.35, 0.35, 0.35);
        float[] colorF = {1.0F, 1.0F, 1.0F};
        if (result != null) {
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) result;
                Direction direction = blockHitResult.getDirection();
                if (direction == Direction.SOUTH) {
                    bb = new AABB(0.0, 0.0, 0.0, 0.5, 0.5, 0.1);
                } else if (direction == Direction.NORTH) {
                    bb = new AABB(0.0, 0.0, 0.4, 0.5, 0.5, 0.5);
                } else if (direction == Direction.EAST) {
                    bb = new AABB(0.0, 0.0, 0.0, 0.1, 0.5, 0.5);
                } else if (direction == Direction.WEST) {
                    bb = new AABB(0.4, 0.0, 0.0, 0.5, 0.5, 0.5);
                } else if (direction == Direction.UP) {
                    colorF = new float[]{0.0F, 1.0F, 0.0F};
                    bb = new AABB(0.0, 0.0, 0.0, 0.5, 0.1, 0.5);
                } else if (direction == Direction.DOWN) {
                    bb = new AABB(0.0, 0.4, 0.0, 0.5, 0.5, 0.5);
                }
            } else if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHitResult = (EntityHitResult) result;
                colorF = new float[]{1.0F, 0.0F, 0.0F};
                RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.5F);
                Entity entity = entityHitResult.getEntity();
                double motionX = entity.getX() - entity.xo;
                double motionY = entity.getY() - entity.yo;
                double motionZ = entity.getZ() - entity.zo;
                AABB move = entity.getBoundingBox()
                        .move(-cameraPos.x, -cameraPos.y, -cameraPos.z)
                        .move(-motionX, -motionY, -motionZ)
                        .move(partialTicks * motionX, partialTicks * motionY, partialTicks * motionZ)
                        .inflate(0.1);
                WorldRenderHelper.drawSolidBox(move, matrixStack);
            }
        }

        double renderX = end.x - cameraPos.x;
        double renderY = end.y - cameraPos.y;
        double renderZ = end.z - cameraPos.z;
        matrixStack.pushPose();
        matrixStack.translate(renderX - 0.25, renderY - 0.25, renderZ - 0.25);
        RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.25F);
        WorldRenderHelper.drawSolidBox(bb, matrixStack);
        RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.75F);
        WorldRenderHelper.drawOutlinedBox(bb, matrixStack);
        matrixStack.popPose();
    }

    /**
     * 手持投掷物的预测路径（照搬 Naven {@code Projectile.getPath}）。
     *
     * @return 手上不是可投掷物时返回 {@code null}；否则返回路径与命中结果（可能为空路径）
     */
    private Path getPath(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ArrayList<Vec3> path = new ArrayList<>();
        ItemStack stack = player.getMainHandItem();
        Item item = stack.getItem();
        if (stack.isEmpty() || !this.isThrowable(item)) {
            return null;
        }
        double arrowPosX = player.xOld + (player.getX() - player.xOld) * partialTicks;
        double arrowPosY = player.yOld + (player.getY() - player.yOld) * partialTicks + player.getEyeHeight() - 0.1;
        double arrowPosZ = player.zOld + (player.getZ() - player.zOld) * partialTicks;
        double arrowMotionFactor = item instanceof ProjectileWeaponItem ? 1.0 : 0.4;
        double yaw;
        double pitch;
        if (RotationHandler.isRotating) {
            // Naven 取 RotationManager 的动画旋转（上一帧 → 当前帧插值）；cookie 的
            // RotationHandler 只保留目标旋转，故直接使用目标值。
            if (RotationHandler.targetRotation == null) {
                return new Path(path, null);
            }
            yaw = Math.toRadians(RotationHandler.targetRotation.getYaw());
            pitch = Math.toRadians(RotationHandler.targetRotation.getPitch());
        } else {
            yaw = Math.toRadians(Mth.lerp(partialTicks, player.yRotO, player.getYRot()));
            pitch = Math.toRadians(Mth.lerp(partialTicks, player.xRotO, player.getXRot()));
        }

        double arrowMotionX = -Math.sin(yaw) * Math.cos(pitch) * arrowMotionFactor;
        double arrowMotionY = -Math.sin(pitch) * arrowMotionFactor;
        double arrowMotionZ = Math.cos(yaw) * Math.cos(pitch) * arrowMotionFactor;
        double arrowMotion = Math.sqrt(arrowMotionX * arrowMotionX + arrowMotionY * arrowMotionY
                + arrowMotionZ * arrowMotionZ);
        arrowMotionX /= arrowMotion;
        arrowMotionY /= arrowMotion;
        arrowMotionZ /= arrowMotion;
        if (item instanceof ProjectileWeaponItem) {
            float bowPower = (float) (72000 - player.getUseItemRemainingTicks()) / 20.0F;
            bowPower = (bowPower * bowPower + bowPower * 2.0F) / 3.0F;
            if (bowPower > 1.0F || bowPower <= 0.1F) {
                bowPower = 1.0F;
            }
            bowPower *= 3.0F;
            arrowMotionX *= bowPower;
            arrowMotionY *= bowPower;
            arrowMotionZ *= bowPower;
        } else {
            arrowMotionX *= 1.5;
            arrowMotionY *= 1.5;
            arrowMotionZ *= 1.5;
        }

        double gravity = this.getProjectileGravity(item);

        for (int i = 0; i < 1000; i++) {
            Vec3 arrowPos = new Vec3(arrowPosX, arrowPosY, arrowPosZ);
            Vec3 postArrowPos = new Vec3(arrowPosX + arrowMotionX, arrowPosY + arrowMotionY, arrowPosZ + arrowMotionZ);
            path.add(arrowPos);
            ClipContext context = new ClipContext(arrowPos, postArrowPos, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mc.player);
            BlockHitResult clip = mc.level.clip(context);
            if (clip.getType() != HitResult.Type.MISS) {
                return new Path(path, clip);
            }

            Arrow fakeArrow = new Arrow(mc.level, arrowPosX, arrowPosY, arrowPosZ);
            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                    mc.level,
                    fakeArrow,
                    arrowPos,
                    postArrowPos,
                    fakeArrow.getBoundingBox().expandTowards(new Vec3(arrowMotionX, arrowMotionY, arrowMotionZ))
                            .inflate(1.0),
                    entity -> entity != player && entity instanceof LivingEntity);
            if (entityHitResult != null && entityHitResult.getType() == HitResult.Type.ENTITY) {
                return new Path(path, entityHitResult);
            }

            arrowPosX += arrowMotionX;
            arrowPosY += arrowMotionY;
            arrowPosZ += arrowMotionZ;
            arrowMotionX *= 0.99;
            arrowMotionY *= 0.99;
            arrowMotionZ *= 0.99;
            arrowMotionY -= gravity;
        }

        return new Path(path, null);
    }

    /** 逐物品重力表（照搬 Naven）。 */
    private double getProjectileGravity(Item item) {
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return 0.05;
        } else if (item instanceof PotionItem) {
            return 0.4;
        } else if (item instanceof FishingRodItem) {
            return 0.15;
        } else if (item instanceof TridentItem) {
            return 0.015;
        } else {
            return 0.03;
        }
    }

    /** 主手物是否属于可投掷类（照搬 Naven）。 */
    private boolean isThrowable(Item item) {
        return item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof EnderpearlItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof FishingRodItem
                || item instanceof TridentItem;
    }

    /** 预测结果（对应 Naven 内部类 {@code Projectile.Path}）。 */
    public record Path(List<Vec3> path, HitResult result) {
    }
}
