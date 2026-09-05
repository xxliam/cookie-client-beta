package xxliam.cookieclient.utils.rotation;

import net.minecraft.util.Mth;

/**
 * 旋转（yaw / pitch）数据类，对齐 OpenZen 的 {@code shit.zen.utils.rotation.Rotation}（去 lombok，精简）。
 */
public class Rotation {

    private float yaw;
    private float pitch;

    public Rotation() {
        this(0.0f, 0.0f);
    }

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setYawPitch(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Rotation clone() {
        return new Rotation(yaw, pitch);
    }

    /** 朝目标角度按最大步长逼近。 */
    public static float moveTowards(float current, float target, float maxStep) {
        float diff = Mth.wrapDegrees(target - current);
        if (diff > maxStep) {
            diff = maxStep;
        }
        if (diff < -maxStep) {
            diff = -maxStep;
        }
        return current + diff;
    }
}
