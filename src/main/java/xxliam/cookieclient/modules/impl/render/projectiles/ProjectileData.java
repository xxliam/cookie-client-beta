package xxliam.cookieclient.modules.impl.render.projectiles;

import net.minecraft.world.entity.Entity;

import java.awt.Color;

/**
 * 弹体数据表（照搬 Naven {@code projectiles.ProjectileData}）。
 * <p>
 * 决定某类弹体的拖尾颜色、每步碰撞盒尺寸（{@code data1} 半宽、{@code data2} 高）与重力。
 * {@code getColor} 形参保持 Naven 的 {@code Object} 签名以逐字对应。
 */
public interface ProjectileData {

    Color getColor(Object entity);

    default float getData1() {
        return 0.125F;
    }

    boolean isTargetEntity(Entity entity);

    default float getData2() {
        return 0.25F;
    }

    default float getGravity() {
        return 0.03F;
    }
}
