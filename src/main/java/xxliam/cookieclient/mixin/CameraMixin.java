package xxliam.cookieclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xxliam.cookieclient.modules.impl.render.MotionCamera;

/**
 * MotionCamera 注入：动态第三人称平滑相机。
 * <p>
 * 对应 1.21.6 原版 {@code CameraMixin}（拦 {@code Camera.update -> setPos}）；1.20.1
 * mojmap 中该方法为 {@code Camera.setup}，设置基础点的方法是 {@code setPosition(DDD)V}。
 * vanilla 的第三人称后移 + 碰撞发生在 setup 内紧随其后的 {@code move()}，因此只需改写
 * 这个基础点（玩家眼睛插值位置），后续 move 会自动从平滑点上完成后移与贴墙，语义与原版
 * 截图实现一致——不可改用 TAIL 覆盖（会丢失 move 的碰撞吸附）。
 * <p>
 * 第一人称必须放行原值并复位模块状态（相机须锁定玩家眼位，且避免切回第三人称时旧平滑点
 * 残留导致瞬跳）。仅当 {@code MotionCamera} 启用时生效。
 */
@Mixin(Camera.class)
public class CameraMixin {

    @ModifyArgs(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V")
    )
    private void cookieClient$motionCamera(Args args) {
        MotionCamera mod = MotionCamera.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        CameraType cameraType = mc.options.getCameraType();
        if (cameraType == null || cameraType.isFirstPerson()) {
            // 第一人称：vanilla 相机必须锁定眼位；顺带复位平滑状态，防切回第三人称首帧瞬跳
            mod.reset();
            return;
        }
        double x = args.get(0);
        double y = args.get(1);
        double z = args.get(2);
        Vec3 smoothed = mod.update(new Vec3(x, y, z));
        if (smoothed != null) {
            args.set(0, smoothed.x);
            args.set(1, smoothed.y);
            args.set(2, smoothed.z);
        }
    }
}
