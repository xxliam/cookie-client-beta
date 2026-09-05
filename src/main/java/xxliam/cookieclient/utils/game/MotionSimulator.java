package xxliam.cookieclient.utils.game;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 运动模拟器：模拟玩家未来若干刻的运动轨迹（对齐 OpenZen 的 MotionSimulator）。
 */
public class MotionSimulator {

    public double x;
    public double y;
    public double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final float yaw;
    private final float strafeSpeed;
    private final float forwardSpeed;
    private float jumpPower;

    public MotionSimulator(Player player) {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.motionX = player.getDeltaMovement().x;
        this.motionY = player.getDeltaMovement().y;
        this.motionZ = player.getDeltaMovement().z;
        this.yaw = player.getYRot();
        this.strafeSpeed = player.xxa;
        this.forwardSpeed = player.zza;
        float currentJumpFactor = player.level().getBlockState(player.blockPosition()).getBlock().getJumpFactor();
        float belowJumpFactor = player.level().getBlockState(player.getOnPos()).getBlock().getJumpFactor();
        this.jumpPower = 0.42f * (currentJumpFactor == 1.0f ? belowJumpFactor : currentJumpFactor) + player.getJumpBoostPower();
    }

    public void simulate(int ticks) {
        for (int i = 0; i < ticks; ++i) {
            tick(false);
        }
    }

    public void simulateWithFriction(int ticks) {
        for (int i = 0; i < ticks; ++i) {
            tick(true);
        }
    }

    private void tick(boolean withFriction) {
        float strafe = withFriction ? this.strafeSpeed * 0.98f : this.strafeSpeed;
        float forward = withFriction ? this.forwardSpeed * 0.98f : this.forwardSpeed;
        float magSqr = strafe * strafe + forward * forward;
        if (magSqr >= 1.0E-4f) {
            if ((magSqr = Mth.sqrt(magSqr)) < 1.0f) {
                magSqr = 1.0f;
            }
            float speed = this.jumpPower;
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isSprinting()) {
                speed *= 1.3f;
            }
            magSqr = speed / magSqr;
            float sinYaw = Mth.sin(this.yaw * (float) Math.PI / 180.0f);
            float cosYaw = Mth.cos(this.yaw * (float) Math.PI / 180.0f);
            this.motionX += (strafe *= magSqr) * cosYaw - (forward *= magSqr) * sinYaw;
            this.motionZ += forward * cosYaw + strafe * sinYaw;
        }
        this.motionY -= 0.08;
        this.motionY *= 0.98f;
        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;
        if (withFriction) {
            this.motionX *= 0.91;
            this.motionZ *= 0.91;
        }
    }
}
