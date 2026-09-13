package xxliam.cookieclient.modules.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.NumberSetting;

/**
 * MotionCamera：动态第三人称（平滑跟随相机）。
 * <p>
 * 移植自某 1.21.6 Fabric client 的 MotionCamera + CameraMixin。cookie(1.20.1 mojmap)
 * 适配差异见 {@link xxliam.cookieclient.mixin.CameraMixin}：新版本 Camera 的
 * {@code update}/{@code setPos} 在 1.20.1 对应 {@code setup}/{@code setPosition}，且
 * vanilla 的第三人称后移 + 碰撞发生在 {@code move()}，因此注入点取 setup TAIL——
 * 读 vanilla 最终相机位置（含碰撞）作为逼近目标，平滑后再整体覆盖，相机才不会瞬跳。
 * <p>
 * 效果：相机位置按距离自适应指数逼近 vanilla 目标——运动时相机拖后、静止时缓缓归位，
 * 产生"动态"弹性感；横向（垂直于视线）分量再按 {@code Turn speed} 限速，玩家快速转身 /
 * strafe 时相机以受限速度平滑绕到身后，而非整条后移向量瞬间横甩（即"减少左右摆动"）。
 */
public class MotionCamera extends Module {

    public static MotionCamera INSTANCE;

    /** 相机与目标距离超过该值时直接瞬移（防换世界/传送后长期悬空追赶）。 */
    private final NumberSetting maxDistance = new NumberSetting("Max distance", 12.0d, 2.0d, 40.0d, 0.5d);
    /** 距离自适应指数逼近基准速率（越大追得越快、动态感越弱）。 */
    private final NumberSetting smoothness = new NumberSetting("Smoothness", 0.5d, 0.1d, 1.0d, 0.05d);
    /**
     * 横向（垂直于视线）最大移动速度（格/秒）。横向分量的每帧位移被钳制在此上限内：
     * 数值越小左右摆幅越小（快速转身时相机以低速绕到身后）；调大则更跟手、更"甩"。
     */
    private final NumberSetting turnSpeed = new NumberSetting("Turn speed", 8.0d, 1.0d, 32.0d, 1.0d);

    /** 平滑后的相机位置（世界坐标）。 */
    private Vec3 cameraPos;
    private boolean initialized;

    public MotionCamera() {
        super("MotionCamera", Category.RENDER);
        INSTANCE = this;
        addSetting(maxDistance);
        addSetting(smoothness);
        addSetting(turnSpeed);
    }

    @Override
    protected void onEnable() {
        reset();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        reset();
        super.onDisable();
    }

    /** 复位平滑状态（模块启停 / 切回第一人称时调用，避免旧位置残留导致瞬跳）。 */
    public void reset() {
        cameraPos = null;
        initialized = false;
    }

    /** 当前平滑相机位置（未被占用时返回 null，mixin 据此放行 vanilla）。 */
    public Vec3 getCameraPos() {
        return cameraPos;
    }

    /**
     * 每帧（CameraMixin 在 setup TAIL 调用）以 vanilla 最终相机位置为逼近目标做一步平滑。
     * 仅在第三人称且模块启用时被调用。
     *
     * @param target vanilla 算好的最终相机位置（含第三人称后移 + 碰撞）
     * @return 本帧应写回的相机位置
     */
    public Vec3 update(Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return target;
        }
        // 首帧吸附：直接落位，避免开局巨大跳变
        if (!initialized || cameraPos == null) {
            cameraPos = target;
            initialized = true;
            return cameraPos;
        }

        Vec3 current = cameraPos;
        double distance = current.distanceTo(target);
        double maxDist = maxDistance.getValue().doubleValue();
        if (distance > maxDist) {
            cameraPos = target;
            return cameraPos;
        }

        double smooth = smoothness.getValue().doubleValue();
        double factor = Mth.clamp(smooth * (1.0 - Math.exp(-distance / Math.max(0.01, maxDist))), 0.0, 1.0);

        // 水平视线方向（Mojang yaw：(-sin, 0, cos)），right 为其水平垂直向量
        double yawRad = Math.toRadians(player.getYRot());
        double fX = -Math.sin(yawRad);
        double fZ = Math.cos(yawRad);
        double rX = -fZ;
        double rZ = fX;

        Vec3 delta = target.subtract(current);
        double along = delta.x * fX + delta.z * fZ; // 前后分量（带符号）
        double lat = delta.x * rX + delta.z * rZ;   // 左右分量（带符号）

        double alongMove = along * factor;
        double yMove = delta.y * factor;
        // 横向限速：factor 收敛的理想步长与 Turn speed 上限取小者——抑制左右摆动
        double latMove = lat * factor;
        double latLimit = Math.max(0.001, turnSpeed.getValue().doubleValue() / 20.0);
        latMove = Mth.clamp(latMove, -latLimit, latLimit);

        cameraPos = current.add(fX * alongMove + rX * latMove, yMove, fZ * alongMove + rZ * latMove);
        return cameraPos;
    }
}
