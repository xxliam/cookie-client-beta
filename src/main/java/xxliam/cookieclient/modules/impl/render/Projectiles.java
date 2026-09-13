package xxliam.cookieclient.modules.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Projectiles：投射物追踪（cookie 版最小子集，仅供 EventAlertHud 消费）。
 * <p>
 * OpenZen 原版是完整 3D 轨迹渲染 + ESP 着色体系；本移植只保留 EventAlertHud 依赖的
 * 数据源：敌方末影珍珠飞行时按 tick 预测落点，写入 {@link #projectileMap}
 * （key = 实体 id），落点/落地后从 map 剔除。预测物理与 zen
 * {@code buildProjectileEntry} 一致：逐 tick 推进、drag 0.99、重力 0.03、方块碰撞截止。
 * 字段命名保留 zen 原版（{@code getX()} = flightTime、{@code getZ()} = distance、
 * {@code getVelocity()} = 预测落点），供 EventAlertHud 的
 * {@code "%.1fs · %.1fm"} 描述与就近排序直接使用。
 */
public class Projectiles extends Module {

    public static Projectiles INSTANCE;

    /** 敌方珍珠落点预测缓存（entityId → 落点条目）。 */
    public static final ConcurrentHashMap<Integer, ProjectileEntry> projectileMap = new ConcurrentHashMap<>();

    /** 珍珠飞行预测条目（字段照搬 zen；velocity 实为预测落点）。 */
    public static final class ProjectileEntry {
        private final String name;
        private final double flightTime;
        private final double distance;
        private final long timestamp;
        private final Item item;
        private final Vec3 velocity;

        public ProjectileEntry(String name, double flightTime, double distance, long timestamp, Item item, Vec3 velocity) {
            this.name = name;
            this.flightTime = flightTime;
            this.distance = distance;
            this.timestamp = timestamp;
            this.item = item;
            this.velocity = velocity;
        }

        public String getName() {
            return name;
        }

        /** 剩余飞行时间（秒），对应 zen ProjectileEntry.getX()。 */
        public double getX() {
            return flightTime;
        }

        /** 距玩家距离（米），对应 zen ProjectileEntry.getZ()。 */
        public double getZ() {
            return distance;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Item getItem() {
            return item;
        }

        /** 预测落点。 */
        public Vec3 getVelocity() {
            return velocity;
        }
    }

    public Projectiles() {
        super("Projectiles", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 清理：已消失/落地/非珍珠/自己投掷的旧条目
        projectileMap.keySet().removeIf(id -> {
            Entity entity = mc.level.getEntity(id);
            return entity == null || !entity.isAlive() || entity.onGround() || !(entity instanceof ThrownEnderpearl)
                    || mc.player.equals(((ThrownEnderpearl) entity).getOwner());
        });
        // 为每颗在飞行的敌方珍珠刷新预测
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl pearl) || !entity.isAlive() || entity.onGround()) {
                continue;
            }
            Entity owner = pearl.getOwner();
            if (owner == null || owner.equals(mc.player)) {
                continue;
            }
            ProjectileEntry entry = buildProjectileEntry(pearl);
            if (entry != null) {
                projectileMap.put(entity.getId(), entry);
            }
        }
    }

    /** 珍珠落点预测（照搬 zen 算法，命中即返回该步终点/时间/距离）。 */
    private ProjectileEntry buildProjectileEntry(ThrownEnderpearl pearl) {
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
        for (int step = 0; step < 1000; ++step) {
            Vec3 start = new Vec3(x, y, z);
            Vec3 end = new Vec3(x + dx, y + dy, z + dz);
            ClipContext clip = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pearl);
            HitResult hit = mc.level.clip(clip);
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                Vec3 hitLoc = hit.getLocation();
                String ownerName = pearl.getOwner() != null ? pearl.getOwner().getName().getString() : "Unknown";
                double flightTime = step / 20.0;
                double distance = mc.player.getEyePosition().distanceTo(hitLoc);
                return new ProjectileEntry(ownerName, flightTime, distance, System.currentTimeMillis(),
                        Items.ENDER_PEARL, hitLoc);
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
}
