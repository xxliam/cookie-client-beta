package xxliam.cookieclient.utils.animation;

/**
 * 弹簧动画：刚度 / 质量 / 阻尼的显式物理积分（半隐式欧拉）。
 * <p>
 * 照搬 OpenZen {@code shit.zen.utils.animation.SpringAnimation}（构造参数
 * stiffness / mass / damping / 初始值逐字一致），用于 DynamicIsland 的
 * 宽度 / 高度 / 转场弹簧。
 */
public class SpringAnimation {

    private final float stiffness;
    private final float mass;
    private final float damping;
    private float targetValue;
    private float currentValue;
    private float velocity;

    public SpringAnimation(float stiffness, float mass, float damping, float initialValue) {
        this.stiffness = stiffness;
        this.mass = mass;
        this.damping = damping;
        this.currentValue = initialValue;
        this.targetValue = initialValue;
    }

    public void reset(float value) {
        this.currentValue = value;
        this.targetValue = value;
        this.velocity = 0.0f;
    }

    public void update(float deltaTime) {
        if (deltaTime <= 0.0f) {
            return;
        }
        float force = -this.stiffness * (this.currentValue - this.targetValue) - this.damping * this.velocity;
        float acceleration = force / this.mass;
        this.velocity += acceleration * deltaTime;
        this.currentValue += this.velocity * deltaTime;
    }

    public float getValue() {
        return this.currentValue;
    }

    public float getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(float targetValue) {
        this.targetValue = targetValue;
    }

    public void setValue(float value) {
        this.currentValue = value;
    }
}
