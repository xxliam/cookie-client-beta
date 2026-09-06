package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.NewClickGui;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.RenderHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 分类面板：标题栏 + 模块列表，支持拖拽、滚动、缩放与折叠动画。
 * <p>
 * 搬运自 OpenZen 的 {@code shit.zen.gui.newclickgui.CategoryPanel}，
 * 裁剪由 stencil 改为 scissor，去 lombok / ClientBase。
 */
public class CategoryPanel extends UIElement {

    public static final int BG_COLOR = ColorUtil.fromRGB(23, 23, 23);
    public static final int ACCENT_COLOR_DARK = new Color(-13768502).darker().darker().getRGB();
    public static final int ACCENT_COLOR = new Color(-13768502).darker().getRGB();

    private final List<ModuleElement> moduleElements = new ArrayList<>();
    private final Category category;
    private float posX;
    private float posY;
    private float panelHeight;
    private boolean isHovered;
    private boolean isDragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private float scrollAmount;
    private float prevHeight;
    private SettingElement<?> hoveredSettingElement;
    private String tooltipText = "";
    private String tooltipText2 = "";
    private boolean isCollapsed;
    private boolean showTooltip;

    private final SmoothAnimationTimer scaleTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer scrollTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer tooltipTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer collapseTimer = new SmoothAnimationTimer();

    public CategoryPanel(Category category) {
        this.category = category;
        for (Module module : CookieClient.MODULE_MANAGER.getModules(category)) {
            moduleElements.add(new ModuleElement(this, module));
        }
        panelHeight = 20.0f + Math.min(240.0f, 20.0f * moduleElements.size());
    }

    /** 面板内容区可用最大高度：扩展到屏幕底部留白（下限 240 兼容小 GUI 缩放，上限随窗口高度）。 */
    private float maxContentHeight() {
        return Math.max(240.0f, Minecraft.getInstance().getWindow().getGuiScaledHeight() - 76.0f);
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        isHovered = CursorUtil.isInBounds(mouseX, mouseY, posX, posY, 120.0f, panelHeight);
        if (isHovered) {
            NewClickGui.focusedPanel = this;
        }
        // 面板入场动画；closing（真正关闭）或 hidden（右下按钮折叠，非关闭）时同样收拢
        boolean invisible = clickGui.isClosing() || clickGui.isHidden();
        scaleTimer.animate(invisible ? 0.0 : 1.0, invisible ? 0.22 : 0.32, Easings.BACK_OUT);
        scaleTimer.tick();
        float totalContentHeight = 0.0f;
        for (ModuleElement moduleElement : moduleElements) {
            totalContentHeight += moduleElement.getHeight();
        }
        // 面板随内容增长到屏幕可用高度：原上限 240（模块>=10 时固定 260 不增长）在新增 HUD 模块后不够用。
        panelHeight = Math.min(totalContentHeight, maxContentHeight()) + 20.0f;
        scrollAmount = Mth.clamp(scrollAmount, 0.0f, totalContentHeight - panelHeight + 20.0f);
        scrollTimer.animate(scrollAmount, 0.22, Easings.EASE_OUT_POW2);
        scrollTimer.tick();
        tooltipTimer.animate(showTooltip ? 1.0 : 0.0, 0.3, Easings.EASE_OUT_POW2);
        tooltipTimer.tick();
        if (isDragging) {
            posX = mouseX + dragOffsetX;
            posY = mouseY + dragOffsetY;
        }
        collapseTimer.animate(isCollapsed ? 0.0 : 1.0, 0.2, Easings.EASE_OUT_POW2);
        collapseTimer.tick();
        if (!isCollapsed) {
            prevHeight = panelHeight;
        }
        float scaleAmount = scaleTimer.getValueF();
        RenderHelper.pushScaleAround(poseStack, posX + 60.0f, posY + panelHeight / 2.0f, 0.4f + 0.6f * scaleAmount);
        float shadowSize = 12.0f;
        Renderer.drawRoundedRect(poseStack, posX - shadowSize, posY - shadowSize, 120.0f + shadowSize * 2.0f, panelHeight + shadowSize * 2.0f,
                6.0f + shadowSize / 2.0f, shadowSize, ColorUtil.fromARGB(0, 0, 0, (int) (80.0f * alpha)));
        Renderer.drawRoundedRect(poseStack, posX, posY, 120.0f, panelHeight, 6.0f, ColorUtil.withAlpha(BG_COLOR, alpha));
        FontStore.AXIFORMA_EXTRABOLD_18.drawString(poseStack, category.displayName,
                posX + 8.0f, posY + (20.0f - FontStore.AXIFORMA_EXTRABOLD_18.getFontHeight()) / 2.0f + 3.0f, ColorUtil.withAlpha(-1, alpha));
        float scrollOffset = scrollTimer.getValueF();
        float elementY = posY + 20.0f - scrollOffset;
        // scissor 不受 Pose 矩阵影响：模块列表裁剪区域须换算到 GUI_SCALE 整体缩放后的屏幕坐标。
        // 注意：要走 pushScissorScreen（输入已含整体缩放系数，仅内部再 ×guiScale），绝不能再用
        // pushScissor，否则双重缩放（×GUI_SCALE 又 ×guiScale）会让裁剪矩形失配而溢出。
        int clipX = Math.round(clickGui.toScaledX(posX));
        int clipY = Math.round(clickGui.toScaledY(posY + 20.0f));
        int clipW = Math.max(1, Math.round(120.0f * NewClickGui.GUI_SCALE));
        int clipH = Math.max(1, Math.round((panelHeight - 20.0f) * NewClickGui.GUI_SCALE));
        Renderer.pushScissorScreen(clipX, clipY, clipW, clipH);
        for (ModuleElement moduleElement : moduleElements) {
            moduleElement.setX(posX);
            moduleElement.setY(elementY);
            moduleElement.render(clickGui, guiGraphics, poseStack, mouseX, mouseY, alpha, partialTicks);
            elementY += moduleElement.getHeight();
        }
        Renderer.drawGradientV(poseStack, posX + 0.5f, posY + 20.0f - 0.5f, 119.0f, 6.0f,
                ColorUtil.withAlpha(-16777216, 0.36f * alpha), ColorUtil.withAlpha(-16777216, 0.0f));
        Renderer.popScissor();
        float tooltipAmount = tooltipTimer.getValueF();
        if (tooltipAmount > 0.0f) {
            float tooltipWidth = FontStore.AXIFORMA_REGULAR_16.getStringWidth(tooltipText);
            Renderer.drawRoundedRect(poseStack, mouseX + 5, mouseY + 5, tooltipWidth + 6.0f,
                    FontStore.AXIFORMA_REGULAR_16.getFontHeight() + 4.0f, 3.0f, ColorUtil.withAlpha(BG_COLOR, alpha * tooltipAmount));
            FontStore.AXIFORMA_REGULAR_16.drawString(poseStack, tooltipText, mouseX + 5 + 3, mouseY + 5 + 1,
                    ColorUtil.withAlpha(-1, alpha * tooltipAmount));
        }
        RenderHelper.popPose(poseStack);
    }

    @Override
    public void reset() {
        scaleTimer.setFromValue(0.0);
        scaleTimer.setCurrentValue(0.0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered) {
            if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY, 120.0f, 20.0f)) {
                isDragging = true;
                dragOffsetX = posX - (float) mouseX;
                dragOffsetY = posY - (float) mouseY;
            } else if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, posX, posY + 20.0f, 120.0f, panelHeight - 20.0f)) {
                for (ModuleElement moduleElement : moduleElements) {
                    if (moduleElement.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ModuleElement moduleElement : moduleElements) {
            if (moduleElement.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        isDragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (isHovered) {
            scrollAmount -= (float) scrollDelta * 50.0f;
            return true;
        }
        return false;
    }

    public List<ModuleElement> getModuleElements() {
        return moduleElements;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isHovered() {
        return isHovered;
    }

    public boolean isDragging() {
        return isDragging;
    }

    public SettingElement<?> getHoveredSettingElement() {
        return hoveredSettingElement;
    }

    public void setHoveredSettingElement(SettingElement<?> element) {
        this.hoveredSettingElement = element;
    }

    public String getTooltipText() {
        return tooltipText;
    }

    public void setTooltipText(String text) {
        this.tooltipText = text;
    }

    public boolean isShowTooltip() {
        return showTooltip;
    }

    public void setShowTooltip(boolean show) {
        this.showTooltip = show;
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.isCollapsed = collapsed;
    }

    @Override
    public float getX() {
        return posX;
    }

    @Override
    public float getY() {
        return posY;
    }

    @Override
    public float getHeight() {
        return panelHeight;
    }

    @Override
    public void setX(float x) {
        this.posX = x;
    }

    @Override
    public void setY(float y) {
        this.posY = y;
    }

    @Override
    public void setHeight(float height) {
        this.panelHeight = height;
    }
}
