package xxliam.cookieclient.modules.impl.render.projectiles;

import net.minecraft.world.entity.projectile.Arrow;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;

/**
 * 箭类弹体数据（照搬 Naven {@code datas.EntityArrowData}）：红 {@code (255,0,0)}，
 * 碰撞盒半宽 0.25 / 高 0.5、重力 0.05。
 */
public class EntityArrowData extends BasicProjectileData {

    public EntityArrowData() {
        super(new HashSet<>(Collections.singletonList(Arrow.class)), new Color(255, 0, 0));
    }

    @Override
    public float getData1() {
        return 0.25F;
    }

    @Override
    public float getData2() {
        return 0.5F;
    }

    @Override
    public float getGravity() {
        return 0.05F;
    }
}
