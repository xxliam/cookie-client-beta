package xxliam.cookieclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 模板裁剪工具，搬运自 OpenZen 的 {@code shit.zen.render.StencilHelper}。
 * <p>
 * 用途：把任意内容裁进「圆角矩形」形状（scissor 只能裁直角矩形，圆角需要模板缓冲）。
 * zen 的 Panel 风格 ClickGUI 用它把设置区内容裁进圆角面板。
 * <p>
 * <b>与 zen 的两处差异（都是安全化处理，视觉不变）</b>：
 * <ol>
 *     <li>zen 的 {@code setupFBO} 会把主渲染目标的<b>深度附件</b>替换成 DEPTH24_STENCIL8 renderbuffer，
 *         再用反射把 {@code depthBufferId} 置 -1 防止重复附加（窗口 resize 后模板会失效）。
 *         这里改为<b>只额外挂一个 STENCIL_INDEX8 renderbuffer 到 GL_STENCIL_ATTACHMENT</b>，
 *         完全不动深度纹理，并在目标尺寸变化时自动重建 —— 语义等价、副作用更小。</li>
 *     <li>提供 {@link #clipRounded} 包装：模板不可用时自动退回 scissor 裁剪（直角），
 *         保证 GUI 永远不会因为驱动/GL 状态问题整块消失。</li>
 * </ol>
 * 需要关掉模板路径时把 {@link #enabled} 置 false 即可（全部退回 scissor）。
 */
public final class StencilHelper {

    private static final int GL_STENCIL_TEST = 2960;
    private static final int GL_ALWAYS = 519;
    private static final int GL_EQUAL = 514;
    private static final int GL_NOTEQUAL = 517;
    private static final int GL_KEEP = 7680;
    private static final int GL_REPLACE = 7681;
    private static final int GL_STENCIL_BUFFER_BIT = 1024;

    /** 总开关：需要完全绕开模板缓冲（退回 scissor）时置 false。 */
    public static boolean enabled = true;

    /** 运行期可用性：attach 失败后自动置 false，后续所有调用走 scissor 回退。 */
    private static boolean available = true;
    private static int stencilRenderbuffer = -1;
    private static int attachedWidth = -1;
    private static int attachedHeight = -1;
    private static boolean writing;

    private StencilHelper() {
    }

    /**
     * 把 {@code content} 裁进圆角矩形 (x, y, w, h, radius)。
     * <p>
     * 模板可用时走 zen 的三步（写掩码 → 读掩码 → 关模板）；否则退回等价的 scissor 裁剪。
     */
    public static void clipRounded(PoseStack poseStack, float x, float y, float width, float height,
                                   float radius, Runnable content) {
        if (enabled && available) {
            beginWrite(false);
            Renderer.drawRoundedRect(poseStack, x, y, width, height, radius, 0xFFFFFFFF);
            beginRead(true);
            content.run();
            end();
            GlStateManager._colorMask(true, true, true, true);
            return;
        }
        // scissor 回退：把矩形经 Pose 变换到 GUI 逻辑空间后交给 Renderer 的 scissor 栈
        // （pushScissorScreen 内部再 ×guiScale）。
        Matrix4f matrix = poseStack.last().pose();
        Vector4f corner0 = new Vector4f(x, y, 0.0f, 1.0f);
        Vector4f corner1 = new Vector4f(x + width, y + height, 0.0f, 1.0f);
        matrix.transform(corner0);
        matrix.transform(corner1);
        int clipX = Math.round(Math.min(corner0.x(), corner1.x()));
        int clipY = Math.round(Math.min(corner0.y(), corner1.y()));
        int clipW = Math.round(Math.abs(corner1.x() - corner0.x()));
        int clipH = Math.round(Math.abs(corner1.y() - corner0.y()));
        Renderer.pushScissorScreen(clipX, clipY, clipW, clipH);
        content.run();
        Renderer.popScissor();
    }

    /** 开始写模板（{@code keepColor=false} 时只写模板、不写颜色）。 */
    public static void beginWrite(boolean keepColor) {
        if (!enabled || !available) {
            return;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        target.bindWrite(false);
        if (!prepare(target)) {
            return;
        }
        GL11.glEnable(GL_STENCIL_TEST);
        GL11.glClearStencil(0);
        GL11.glClear(GL_STENCIL_BUFFER_BIT);
        GL11.glStencilFunc(GL_ALWAYS, 1, 255);
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        if (!keepColor) {
            GlStateManager._colorMask(false, false, false, false);
        }
        writing = true;
    }

    /** 切换到读模板（{@code inside=true} 表示保留模板内区域）。 */
    public static void beginRead(boolean inside) {
        if (!enabled || !available || !writing) {
            return;
        }
        GL11.glStencilFunc(inside ? GL_EQUAL : GL_NOTEQUAL, 1, 255);
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableBlend();
    }

    /** 关闭模板测试。 */
    public static void end() {
        if (!enabled || !available || !writing) {
            writing = false;
            return;
        }
        GL11.glDisable(GL_STENCIL_TEST);
        writing = false;
    }

    /**
     * 确保主渲染目标上挂着与当前尺寸匹配的模板 renderbuffer。
     *
     * @return 是否可继续使用模板路径
     */
    private static boolean prepare(RenderTarget target) {
        try {
            if (target.width <= 0 || target.height <= 0) {
                return false;
            }
            if (stencilRenderbuffer != -1 && attachedWidth == target.width && attachedHeight == target.height) {
                return true;
            }
            if (stencilRenderbuffer != -1) {
                EXTFramebufferObject.glDeleteRenderbuffersEXT(stencilRenderbuffer);
                stencilRenderbuffer = -1;
            }
            int rbo = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(GL30.GL_RENDERBUFFER, rbo);
            EXTFramebufferObject.glRenderbufferStorageEXT(GL30.GL_RENDERBUFFER, GL30.GL_STENCIL_INDEX8,
                    target.width, target.height);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, rbo);
            int status = EXTFramebufferObject.glCheckFramebufferStatusEXT(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                EXTFramebufferObject.glDeleteRenderbuffersEXT(rbo);
                available = false;
                return false;
            }
            stencilRenderbuffer = rbo;
            attachedWidth = target.width;
            attachedHeight = target.height;
            return true;
        } catch (Throwable throwable) {
            available = false;
            return false;
        }
    }
}
