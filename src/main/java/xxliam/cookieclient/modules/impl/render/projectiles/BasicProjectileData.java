package xxliam.cookieclient.modules.impl.render.projectiles;

import net.minecraft.world.entity.Entity;

import java.awt.Color;
import java.util.Set;

/**
 * 通用弹体数据：一组实体类型 + 固定颜色（照搬 Naven {@code datas.BasicProjectileData}）。
 * <p>
 * {@code data1}/{@code data2}/{@code getGravity} 沿用接口默认值（半宽 0.125、高 0.25、重力 0.03）。
 */
public class BasicProjectileData implements ProjectileData {

    private final Color color;
    private final Set<Class<?>> entityClass;

    public BasicProjectileData(Set<Class<?>> entityClass) {
        this(entityClass, new Color(255, 255, 255));
    }

    public BasicProjectileData(Set<Class<?>> entityClass, Color color) {
        this.entityClass = entityClass;
        this.color = color;
    }

    @Override
    public Color getColor(Object entity) {
        return this.color;
    }

    @Override
    public boolean isTargetEntity(Entity entity) {
        for (Class<?> clazz : this.entityClass) {
            if (clazz.isInstance(entity)) {
                return true;
            }
        }
        return false;
    }
}
