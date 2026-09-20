package xxliam.cookieclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.modules.Module;

/**
 * 世界空间渲染通道：把 {@code WorldRenderEvents.LAST} 转发给所有已启用模块的
 * {@link Module#renderWorld}。
 * <p>
 * 为什么用 Fabric 事件而不是注入 {@code LevelRenderer.renderLevel}：
 * 该事件由 fabric-rendering-v1（fabric-api 自带，无需改构建）在
 * {@code LevelRenderer} 内部正确时机触发，省掉自己维护注入点与 pose 栈平衡的问题。
 * <p>
 * 时机 = 世界画完、手部与 GUI 尚未渲染；传入的 PoseStack 只含相机旋转
 * （1.20.1 世界渲染一律使用相机相对坐标），配合 {@code WorldRenderHelper} 使用。
 */
public final class WorldRenderHook {

    private WorldRenderHook() {
    }

    /** 注册世界渲染回调（仅客户端调用）。 */
    public static void register() {
        WorldRenderEvents.LAST.register(WorldRenderHook::onLast);
    }

    private static void onLast(WorldRenderContext context) {
        if (CookieClient.MODULE_MANAGER == null) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        Camera camera = context.camera();
        float partialTicks = context.tickDelta();
        for (Module module : CookieClient.MODULE_MANAGER.getModules()) {
            if (module.isEnabled()) {
                module.renderWorld(poseStack, camera, partialTicks);
            }
        }
    }
}
