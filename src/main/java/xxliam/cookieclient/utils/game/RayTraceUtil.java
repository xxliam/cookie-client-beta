package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import xxliam.cookieclient.utils.rotation.Rotation;

import java.util.Optional;

/**
 * 射线检测工具（对齐 OpenZen 的 RayTraceUtil）。
 */
public final class RayTraceUtil {

    private RayTraceUtil() {
    }

    public static boolean canRayTrace(Rotation rotation, Direction direction, BlockPos blockPos, boolean checkFace) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        float yaw = rotation.getYaw();
        float pitch = rotation.getPitch();
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookDir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 endPos = eyePos.add(lookDir.scale(5.0));
        BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        boolean samePos = hit.getBlockPos().equals(blockPos);
        boolean sameFace = !checkFace || hit.getDirection() == direction;
        return samePos && sameFace;
    }

    public static HitResult rayTrace(float partialTicks, Rotation rotation) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = null;
        Entity entity = mc.getCameraEntity();
        if (entity != null && mc.level != null) {
            double range = mc.gameMode.getPickRange();
            hit = rayTrace(range, partialTicks, true, rotation.getYaw(), rotation.getPitch());
        }
        return hit;
    }

    public static HitResult rayTrace(double range, float partialTicks, boolean clipFluids, Rotation rotation) {
        return rayTrace(range, partialTicks, clipFluids, rotation.getYaw(), rotation.getPitch());
    }

    public static Vec3 getViewVector(float pitch, float yaw) {
        float yawRad = yaw * ((float) Math.PI / 180);
        float pitchRad = -pitch * ((float) Math.PI / 180);
        float cosPitch = Mth.cos(pitchRad);
        float sinPitch = Mth.sin(pitchRad);
        float cosYaw = Mth.cos(yawRad);
        float sinYaw = Mth.sin(yawRad);
        return new Vec3(sinPitch * cosYaw, -sinYaw, cosPitch * cosYaw);
    }

    public static HitResult rayTrace(double range, float partialTicks, boolean clipFluids, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eyePos = new Vec3(mc.player.getX(), mc.player.getY() + 1.62, mc.player.getZ());
        Vec3 viewVec = getViewVector(pitch, yaw);
        Vec3 endPos = eyePos.add(viewVec.x * range, viewVec.y * range, viewVec.z * range);
        return mc.player.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE,
                clipFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, mc.player));
    }

    public static HitResult clipWithEntity(Vec3 fromPos, Vec3 toPos, boolean clipFluids, boolean useCollider, boolean useVisual, Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        ClipContext.Block blockMode = useCollider ? ClipContext.Block.COLLIDER : (useVisual ? ClipContext.Block.VISUAL : ClipContext.Block.OUTLINE);
        ClipContext.Fluid fluidMode = clipFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        return mc.level.clip(new ClipContext(fromPos, toPos, blockMode, fluidMode, entity));
    }

    public static EntityHitResult getEntityHit(AABB aabb, Vec3 fromPos, Vec3 toPos) {
        Optional<Vec3> clip = aabb.clip(fromPos, toPos);
        return clip.map(hit -> new EntityHitResult(null, hit)).orElse(null);
    }
}
