package xxliam.cookieclient.modules.impl.render.projectiles;

import net.minecraft.world.entity.projectile.ThrownPotion;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;

/**
 * 药水类弹体数据（照搬 Naven {@code datas.EntityPotionData}）：品红 {@code (255,66,249)}，
 * 碰撞盒用接口默认值、重力 0.05。
 */
public class EntityPotionData extends BasicProjectileData {

    public EntityPotionData() {
        super(new HashSet<>(Collections.singleton(ThrownPotion.class)), new Color(255, 66, 249));
    }

    @Override
    public float getGravity() {
        return 0.05F;
    }
}
