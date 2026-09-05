package xxliam.cookieclient.gui.mainmenu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 满屏动态背景（复刻 Setsuna ScreenBackdrop 数值与两态逻辑）：
 * <ul>
 *   <li>图片态：封面居中裁剪铺满 + shade + 边缘暗角；支持 <gameDir>/.cookie-client/ui/menu-background.png 自定义；</li>
 *   <li>网格态（.cookie-client/ui/use-grid-background 标记启用）：对角渐变底 + 透视色带 + 网格视差 + 扫描线 + 边缘暗角。</li>
 * </ul>
 */
public final class BackdropRenderer {

    private static boolean stateLoaded;
    private static boolean gridBackground;

    private BackdropRenderer() {
    }

    private static Path uiDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(".cookie-client").resolve("ui");
    }

    private static Path customBackgroundPath() {
        return uiDirectory().resolve("menu-background.png");
    }

    private static Path gridMarkerPath() {
        return uiDirectory().resolve("use-grid-background");
    }

    /** 主菜单背景（带 160×… shadeAlpha 语义）。图片可用时走 cover 图，否则网格。 */
    public static void drawMainMenu(PoseStack poseStack, float width, float height,
                                    float time, float pointerX, float pointerY, int shadeAlpha) {
        ensureStateLoaded();
        if (gridBackground) {
            drawGridVariant(poseStack, width, height, time, pointerX, pointerY, shadeAlpha);
            return;
        }
        BufferedImage custom = UiImage.sourceFromFile(customBackgroundPath());
        BufferedImage background = custom != null
                ? custom
                : UiImage.sourceFromResource("menu", "/assets/cookie-client/textures/mainmenu/background.png");
        if (background == null || width <= 0.0F || height <= 0.0F) {
            drawGridVariant(poseStack, width, height, time, pointerX, pointerY, shadeAlpha);
            return;
        }
        UiImage.drawCover(poseStack, background, 0.0F, 0.0F, width, height);
        if (shadeAlpha > 0) {
            UiDraw.fill(poseStack, 0.0F, 0.0F, width, height,
                    UiTheme.argb(Math.min(255, shadeAlpha), 3, 4, 7));
        }
        drawEdgeShade(poseStack, width, height);
    }

    /** 网格背景（Alt 管理等次屏默认用带 alpha 底色的网格）。 */
    public static void draw(PoseStack poseStack, float width, float height,
                            float time, float pointerX, float pointerY, int shadeAlpha) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        drawGridVariant(poseStack, width, height, time, pointerX, pointerY, shadeAlpha);
    }

    /** 视点居中版（对应 Setsuna ScreenBackdrop.draw(canvas,w,h,shadeAlpha)）。 */
    public static void draw(PoseStack poseStack, float width, float height, int shadeAlpha) {
        float time = seconds();
        drawGridVariant(poseStack, width, height, time, width * 0.5F, height * 0.5F, shadeAlpha);
    }

    private static void drawGridVariant(PoseStack poseStack, float width, float height,
                                        float time, float pointerX, float pointerY, int shadeAlpha) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        // 对角渐变底色（Skia p0=(0,0) p1=(w,h)，三段停靠照搬）
        UiImage.drawGradientScreen(poseStack, 0.0F, 0.0F, width, height,
                new int[]{0xFF06070A, 0xFF10141A, 0xFF090A0E},
                new float[]{0.0F, 0.58F, 1.0F});

        float parallaxX = clamp((pointerX / width - 0.5F) * 10.0F, -5.0F, 5.0F);
        float parallaxY = clamp((pointerY / height - 0.5F) * 8.0F, -4.0F, 4.0F);
        drawPerspectivePlane(poseStack, width, height, time, parallaxX, parallaxY);
        drawGrid(poseStack, width, height, time, parallaxX, parallaxY);
        drawScan(poseStack, width, height, time);

        if (shadeAlpha > 0) {
            UiDraw.fill(poseStack, 0.0F, 0.0F, width, height,
                    UiTheme.argb(Math.min(255, shadeAlpha), 4, 6, 9));
        }
        drawEdgeShade(poseStack, width, height);
    }

    private static void drawPerspectivePlane(PoseStack poseStack, float width, float height,
                                             float time, float parallaxX, float parallaxY) {
        int accent = UiTheme.accent();
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.55F);
        poseStack.pushPose();
        poseStack.translate(width * 0.73F + parallaxX, height * 0.46F + parallaxY, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-13.0F));
        float planeWidth = Math.max(120.0F, width * 0.22F);
        float planeHeight = height * 2.1F;
        UiDraw.gradientH(poseStack, -planeWidth * 0.5F, -planeHeight * 0.5F, planeWidth, planeHeight,
                new int[]{
                        UiTheme.withAlpha(accent, 0),
                        UiTheme.withAlpha(accent, 12 + Math.round(pulse * 9.0F)),
                        UiTheme.withAlpha(0xFFF1A45D, 8),
                        UiTheme.withAlpha(accent, 0)
                },
                new float[]{0.0F, 0.28F, 0.72F, 1.0F});
        poseStack.popPose();

        // 橙色轨迹斜线（右侧，平移视差）
        float startX = width * 0.82F + parallaxX * 0.7F;
        UiDraw.line(poseStack, startX, height * 0.12F,
                startX - height * 0.22F, height * 0.88F, 1.0F,
                UiTheme.withAlpha(0xFFF1A45D, 38));
    }

    private static void drawGrid(PoseStack poseStack, float width, float height,
                                 float time, float parallaxX, float parallaxY) {
        float spacing = Math.max(34.0F, Math.min(58.0F, width / 12.0F));
        float offsetX = positiveModulo(time * 3.5F + parallaxX, spacing);
        float offsetY = positiveModulo(time * 2.0F + parallaxY, spacing);
        for (float x = -spacing + offsetX; x < width + spacing; x += spacing) {
            UiDraw.line(poseStack, x, 0.0F, x, height, 1.0F, 0x0EFFFFFF);
        }
        for (float y = -spacing + offsetY; y < height + spacing; y += spacing) {
            UiDraw.line(poseStack, 0.0F, y, width, y, 1.0F, 0x0EFFFFFF);
        }
        UiDraw.line(poseStack, width * 0.08F, 0.0F, width * 0.08F, height, 1.0F, 0x18FFFFFF);
        UiDraw.line(poseStack, width * 0.92F, 0.0F, width * 0.92F, height, 1.0F, 0x18FFFFFF);
    }

    private static void drawScan(PoseStack poseStack, float width, float height, float time) {
        float travel = height + 120.0F;
        float y = positiveModulo(time * 23.0F, travel) - 60.0F;
        int accent = UiTheme.accent();
        UiDraw.gradientV(poseStack, 0.0F, y - 38.0F, width, 76.0F,
                new int[]{
                        UiTheme.withAlpha(accent, 0),
                        UiTheme.withAlpha(accent, 11),
                        UiTheme.withAlpha(accent, 0)
                },
                new float[]{0.0F, 0.5F, 1.0F});
        UiDraw.line(poseStack, 0.0F, y, width, y, 1.0F, UiTheme.withAlpha(accent, 24));
    }

    private static void drawEdgeShade(PoseStack poseStack, float width, float height) {
        float edge = Math.min(150.0F, width * 0.24F);
        // 左缘
        xxliam.cookieclient.render.Renderer.drawGradientH(poseStack, 0.0F, 0.0F, edge, height,
                0x8C020306, 0x00020306);
        // 右缘
        xxliam.cookieclient.render.Renderer.drawGradientH(poseStack, width - edge, 0.0F, edge, height,
                0x00020306, 0x76020306);
        // 底缘
        float vertical = Math.min(100.0F, height * 0.25F);
        xxliam.cookieclient.render.Renderer.drawGradientV(poseStack, 0.0F, height - vertical, width, vertical,
                0x00020306, 0x76020306);
    }

    private static void ensureStateLoaded() {
        if (stateLoaded) {
            return;
        }
        gridBackground = Files.isRegularFile(gridMarkerPath());
        stateLoaded = true;
    }

    private static float seconds() {
        return (System.nanoTime() & 0x1FFFFFFFFFFFFFL) / 1_000_000_000.0F;
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
