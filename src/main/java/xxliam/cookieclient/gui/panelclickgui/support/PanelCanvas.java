package xxliam.cookieclient.gui.panelclickgui.support;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import xxliam.cookieclient.render.Rectangle;
import xxliam.cookieclient.render.Renderer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Panel 风格 ClickGUI 的画布包装，对应 OpenZen 的 {@code shit.zen.render.DrawContext} 中
 * 面板真正用到的那几个方法（{@code save / restore / clip / translate / scale}）。
 * <p>
 * 与 zen 的差异：
 * <ul>
 *     <li>zen 的 {@code clip(Rectangle)} 直接把矩形经 Pose 变换后交给 {@code GuiGraphics.enableScissor}
 *         （不与父级求交）；这里走 {@link Renderer#pushScissorScreen}，会与父级裁剪求交，
 *         {@link #restore()} 也会自动恢复父级 scissor（更安全，裁剪结果一致）。</li>
 *     <li>不再需要 zen 的 {@code Renderer.renderConsumer(...)} 画布生命周期（cookie 的 Renderer 是无状态静态工具），
 *         每个 render 调用自建一个 PanelCanvas 即可。</li>
 * </ul>
 */
public final class PanelCanvas {

    private final PoseStack poseStack;
    private final Deque<Boolean> clipStack = new ArrayDeque<>();

    public PanelCanvas(GuiGraphics guiGraphics) {
        this.poseStack = guiGraphics.pose();
    }

    public PoseStack pose() {
        return poseStack;
    }

    /** 保存 Pose + 标记本层未裁剪。 */
    public void save() {
        poseStack.pushPose();
        clipStack.push(Boolean.FALSE);
    }

    /** 恢复 Pose，并在本层开过裁剪时弹出 scissor。 */
    public void restore() {
        if (!clipStack.isEmpty() && Boolean.TRUE.equals(clipStack.pop())) {
            Renderer.popScissor();
        }
        poseStack.popPose();
    }

    public void translate(float x, float y) {
        poseStack.translate(x, y, 0.0f);
    }

    public void scale(float scaleX, float scaleY) {
        poseStack.scale(scaleX, scaleY, 1.0f);
    }

    /** 按当前 Pose 变换后的矩形裁剪（GUI 逻辑坐标 → 屏幕像素，内部与父级求交）。 */
    public void clip(Rectangle rectangle) {
        Matrix4f matrix = poseStack.last().pose();
        Vector4f corner0 = new Vector4f(rectangle.x1(), rectangle.y1(), 0.0f, 1.0f);
        Vector4f corner1 = new Vector4f(rectangle.x2(), rectangle.y2(), 0.0f, 1.0f);
        matrix.transform(corner0);
        matrix.transform(corner1);
        int clipX = Math.round(Math.min(corner0.x(), corner1.x()));
        int clipY = Math.round(Math.min(corner0.y(), corner1.y()));
        int clipW = Math.round(Math.abs(corner1.x() - corner0.x()));
        int clipH = Math.round(Math.abs(corner1.y() - corner0.y()));
        Renderer.pushScissorScreen(clipX, clipY, clipW, clipH);
        // 本层若已 save 过，标记为"开过裁剪"，restore 时弹出；若无 save（直接 clip），
        // 补一个 TRUE 标记，交给 clearClipStack() 在渲染结束时兜底弹出。
        if (clipStack.isEmpty()) {
            clipStack.push(Boolean.TRUE);
        } else {
            clipStack.pop();
            clipStack.push(Boolean.TRUE);
        }
    }

    /** 渲染结束时兜底清空裁剪栈（等价 zen DrawContext.clearClipStack）。 */
    public void clearClipStack() {
        while (!clipStack.isEmpty()) {
            if (Boolean.TRUE.equals(clipStack.pop())) {
                Renderer.popScissor();
            }
        }
    }
}
