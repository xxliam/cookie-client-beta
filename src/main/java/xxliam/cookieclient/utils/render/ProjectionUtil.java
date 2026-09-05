package xxliam.cookieclient.utils.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * 投影工具：把世界坐标投影到 GUI 屏幕坐标。
 * <p>
 * 用相机位置 + 旋转 + 当前 FOV 手动做透视投影，不依赖 {@code RenderSystem} 的矩阵状态，
 * 因此可在 HUD 渲染阶段（{@code Gui.render} 之后）安全使用。
 */
public final class ProjectionUtil {

    private ProjectionUtil() {
    }

    /**
     * 世界坐标投影到屏幕坐标。
     *
     * @return 屏幕坐标（GUI 缩放后的逻辑坐标），在相机后方或不可见时返回 {@code null}
     */
    public static Vector2f project(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Quaternionf rotation = new Quaternionf(camera.rotation()).conjugate();
        Vector3f rel = new Vector3f(
                (float) (cameraPos.x - x),
                (float) (cameraPos.y - y),
                (float) (cameraPos.z - z));
        rel.rotate(rotation);
        if (rel.z >= 0.0f) {
            return null;
        }
        double fov = mc.options.fov().get();
        float halfHeight = mc.getWindow().getGuiScaledHeight() / 2.0f;
        float scale = (float) (halfHeight / (rel.z * Math.tan(Math.toRadians(fov / 2.0))));
        return new Vector2f(
                -rel.x * scale + mc.getWindow().getGuiScaledWidth() / 2.0f,
                mc.getWindow().getGuiScaledHeight() / 2.0f - rel.y * scale);
    }
}
