package xxliam.cookieclient.utils.game;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.CookieClient;

/**
 * 实体工具：存活判断、好友判断、插值位置 / AABB 等。
 */
public final class EntityUtil {

    private EntityUtil() {
    }

    public static boolean isAlive(Entity entity) {
        return entity != null && entity.isAlive();
    }

    public static boolean isFriend(Entity entity) {
        if (entity == null || entity.getDisplayName() == null) {
            return false;
        }
        return CookieClient.TARGET_MANAGER.isFriend(entity.getDisplayName().getString());
    }

    public static boolean isValidTarget(Entity entity) {
        return isAlive(entity) && !isFriend(entity);
    }

    /** 插值后的实体位置（用于平滑渲染）。 */
    public static Vec3 getInterpolatedPos(Entity entity, float partialTicks) {
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

    /** 插值后的实体碰撞箱（对齐 OpenZen 的 getInterpolatedAABB）。 */
    public static AABB getInterpolatedAABB(Entity entity, float partialTicks) {
        Vec3 pos = getInterpolatedPos(entity, partialTicks);
        double halfWidth = entity.getBbWidth() / 2.0;
        return new AABB(pos.x - halfWidth, pos.y, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + entity.getBbHeight(), pos.z + halfWidth);
    }
}
