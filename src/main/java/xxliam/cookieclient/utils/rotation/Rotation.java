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

    /** 逐分量相减（照搬 OpenZen {@code Rotation.subtract}）。 */
    public Rotation subtract(Rotation rotation) {
        return new Rotation(this.yaw - rotation.yaw, this.pitch - rotation.pitch);
    }

    /** 逐分量取反（照搬 OpenZen {@code Rotation.negate}）。 */
    public Rotation negate() {
        return new Rotation(-this.yaw, -this.pitch);
    }

    /**
     * 把 yaw/pitch 吸附到「鼠标灵敏度步长」的整数倍上（照搬 OpenZen {@code Rotation.snapToSensitivity}）。
     * <p>
     * 原版鼠标转向量是 gcd 的整数倍，直接设任意角度会与真实鼠标输入产生不一致；
     * 吸附后旋转量就能被原版鼠标逻辑整除。
     */
    public Rotation snapToSensitivity(float sensitivity) {
        float scaled = sensitivity * 0.6f + 0.2f;
        float step = scaled * scaled * scaled * 1.2f;
        this.yaw -= this.yaw % step;
        this.pitch -= this.pitch % step;
        return this;
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
