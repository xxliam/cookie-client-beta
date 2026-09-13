package xxliam.cookieclient.gui.dropdownclickgui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.dropdownclickgui.Component;
import xxliam.cookieclient.gui.dropdownclickgui.DropdownRender;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ColorUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 分类条（Dropdown ClickGUI 的横向标签）：点按拖动、向下拉出该分类的全部模块。
 * <p>
 * 移植自 OpenOpal {@code wtf.opal.client.screen.click.dropdown.panel.CategoryPanel}，
 * 视觉参数照搬：条高 20、圆角 5（底两角 0）、底色 0x0f0f0f@85%、分类名 productsans-bold 9、
 * 右侧图标 materialicons 10；开合动画 EASE_OUT_SINE、时长 100ms + 面板序号 × 80ms。
 * <p>
 * cookie 侧差异：Category 用本地 7 类枚举（无 opal 的分类 icon 字段，本地建码点映射）；
 * Scroller 用 {@link SmoothAnimationTimer}(EASE_OUT_EXPO 250ms) 重写等价行为。
 */
public class CategoryPanel extends Component {

    public static final float HEADER_HEIGHT = 20.0f;

    private final Category category;
    private final int panelIndex;
    private final boolean lastPanel;
    private final List<ModulePanel> modulePanels = new ArrayList<>();

    private SmoothAnimationTimer openAnim = new SmoothAnimationTimer();
    private boolean closing;

    private boolean draggingAllowed;
    private boolean dragging;
    private float baseX;
    private float baseY;
    private float dragOffsetX;
    private float dragOffsetY;
    private float dragMouseOffsetX;
    private float dragMouseOffsetY;

    // ---- 滚动（复刻 opal Scroller：EASE_OUT_EXPO 250ms 跟手 + 50px/格） ----
    private float scrollOffset;
    private final SmoothAnimationTimer scrollAnim = new SmoothAnimationTimer();

    public CategoryPanel(final Category category, final int panelIndex) {
        this.category = category;
        this.panelIndex = panelIndex;
        this.lastPanel = panelIndex == Category.values().length - 1;
    }

    @Override
    public void init() {
        modulePanels.clear();
        CookieClient.MODULE_MANAGER.getModules(category)
                .forEach(module -> modulePanels.add(new ModulePanel(module)));
        modulePanels.sort(Comparator.comparing(panel -> panel.getModule().getName()));
        // 开合动画：100ms + 序号 × 80ms（opal 入场错峰）
        openAnim = new SmoothAnimationTimer();
        openAnim.setCurrentValue(0.0);
        closing = false;
        scrollOffset = 0.0f;
        modulePanels.forEach(ModulePanel::init);
    }

    @Override
    public void close() {
        closing = true;
        modulePanels.forEach(ModulePanel::close);
    }

    /** Screen 层判定「全部分类收起完成」用。 */
    public boolean isCloseFinished() {
        // 不能依赖 isDone()：render 每帧 animate()+tick() 会重启计时器，progress 回不到 1；
        // 值比较也必须带容差（NewClickGui 用 Mth.equal 同理）：渐近逼近的残差可能长期停在
        // 1e-9 量级，精确 ==0 会永不成立 → Screen 挂着吞输入。
        return closing && openAnim.getValueF() <= 0.0001f;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, float alpha) {
        openAnim.animate(closing ? 0.0 : 1.0, (100.0 + panelIndex * 80.0) / 1000.0, Easings.EASE_OUT_SINE);
        openAnim.tick();
        updateDrag(mouseX, mouseY);

        if (isCloseFinished()) {
            return;
        }

        float currentY = this.y + HEADER_HEIGHT;
        float totalHeight = getTotalHeight();
        float openValue = openAnim.getValueF();

        Minecraft mc = Minecraft.getInstance();
        float relativeScreenHeight = mc.getWindow().getGuiScaledHeight() - this.y;
        float scissorHeight = Math.min(relativeScreenHeight, totalHeight * openValue);
        float scroll = scrollAnim.getValueF();

        Renderer.pushScissor(Math.round(x), Math.round(y), Math.round(width), Math.round(Math.max(0.0f, scissorHeight)));
        float a = alpha * openValue;

        // header：底部两角 0（opal roundedRectVarying 5,5,0,0），后屏模糊层 + 85% 深灰
        drawHeader(guiGraphics, a, scroll);

        for (int i = 0; i < modulePanels.size(); i++) {
            ModulePanel panel = modulePanels.get(i);
            float panelHeight = HEADER_HEIGHT + panel.getExpandAnimation().getValueF() * panel.getAddedHeight();
            panel.setDimensions(x, currentY + scroll, width, panelHeight);
            panel.setLastModule(i == modulePanels.size() - 1);
            panel.render(guiGraphics, mouseX, mouseY, delta, a);
            currentY += panelHeight;
        }
        Renderer.popScissor();

        // 滚动钳制（opal Scroller.onScroll）
        scrollOffset = Math.max(-getMaxOffset(totalHeight), Math.min(0.0f, scrollOffset));
        scrollAnim.animate(scrollOffset, 0.25, Easings.EASE_OUT_EXPO);
        scrollAnim.tick();
    }

    private void drawHeader(GuiGraphics g, float a, float scroll) {
        // blur 层（opal BLUR_PAINT）：drawScreenBlur 每帧帧缓冲捕获一次，画当前区域的毛玻璃
        Renderer.drawScreenBlur(g.pose(), x, y + scroll, width, HEADER_HEIGHT, 5.0f, 2.5f);
        Renderer.drawRoundedRect(g.pose(), x, y + scroll, width, HEADER_HEIGHT,
                5.0f, 5.0f, 0.0f, 0.0f, ColorUtil.applyOpacity(0xFF0F0F0F, 0.85f * a));
        DropdownRender.baseline(g, FontStore.PRODUCTSANS_BOLD_9, category.getDisplayName(),
                x + 5.0f, y + scroll + 13.0f, ColorUtil.withAlpha(-1, a));
        DropdownRender.baseline(g, FontStore.MATERIALICONS_10, icon(category),
                x + width - 15.5f, y + scroll + 15.0f, ColorUtil.withAlpha(-1, a));
    }

    /** cookie Category → materialicons 图标码点（opal 分类带 icon，本地补映射）。 */
    private static String icon(Category category) {
        return switch (category) {
            case COMBAT -> "\ue9e0";   // sports_mma
            case MOVEMENT -> "\ue566"; // directions_run
            case PLAYER -> "\ue87c";   // face
            case RENDER -> "\ue8f4";   // visibility
            case EXPLOIT -> "\ue868";  // bug_report
            case WORLD -> "\ue80b";    // public
            case MISC -> "\ue429";     // tune
        };
    }

    private float getTotalHeight() {
        float totalHeight = HEADER_HEIGHT;
        for (ModulePanel panel : modulePanels) {
            panel.getExpandAnimation().animate(panel.isExpanded() ? 1.0 : 0.0,
                    0.125, Easings.EASE_OUT_QUAD);
            panel.getExpandAnimation().tick();
            totalHeight += HEADER_HEIGHT + panel.getExpandAnimation().getValueF() * panel.getAddedHeight();
        }
        return totalHeight;
    }

    private float getMaxOffset(final float totalHeight) {
        Minecraft mc = Minecraft.getInstance();
        float relativeScreenHeight = mc.getWindow().getGuiScaledHeight() - y;
        float scissorHeight = Math.min(relativeScreenHeight, totalHeight * openAnim.getValueF());
        float overflowPadding = scissorHeight == relativeScreenHeight ? y : 0;
        return Math.max(0, totalHeight - scissorHeight + overflowPadding);
    }

    private void updateDrag(final int mouseX, final int mouseY) {
        if (!draggingAllowed || !dragging || mouseX == -1 || mouseY == -1) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float newX = mouseX - dragMouseOffsetX;
        float newY = mouseY - dragMouseOffsetY;
        float maxX = mc.getWindow().getGuiScaledWidth() - width;
        float maxY = mc.getWindow().getGuiScaledHeight() - HEADER_HEIGHT;
        dragOffsetX = Math.max(-baseX, Math.min(maxX - baseX, newX - baseX));
        dragOffsetY = Math.max(-baseY, Math.min(maxY - baseY, newY - baseY));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingAllowed && isHovering(x, y, width, HEADER_HEIGHT, mouseX, mouseY)) {
            dragging = true;
            dragMouseOffsetX = (float) mouseX - x;
            dragMouseOffsetY = (float) mouseY - y;
            return;
        }
        for (ModulePanel panel : modulePanels) {
            panel.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        for (ModulePanel panel : modulePanels) {
            panel.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float totalHeight = getTotalHeight();
        if (isHovering(x, y, width, totalHeight, mouseX, mouseY)) {
            scrollOffset += (float) (verticalAmount * 50.0);
            scrollOffset = Math.max(-getMaxOffset(totalHeight), Math.min(0.0f, scrollOffset));
        }
        for (ModulePanel panel : modulePanels) {
            panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }

    @Override
    public void keyPressed(int keyCode) {
        for (ModulePanel panel : modulePanels) {
            panel.keyPressed(keyCode);
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        for (ModulePanel panel : modulePanels) {
            panel.charTyped(chr, modifiers);
        }
    }

    private static boolean isHovering(float x, float y, float w, float h, double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void setBasePosition(float baseX, float baseY) {
        this.baseX = baseX;
        this.baseY = baseY;
    }

    public void setDraggingAllowed(boolean draggingAllowed) {
        this.draggingAllowed = draggingAllowed;
        if (!draggingAllowed) {
            this.dragging = false;
        }
    }

    public float getDragOffsetX() {
        return dragOffsetX;
    }

    public float getDragOffsetY() {
        return dragOffsetY;
    }

    public void setDimensions(float x, float y, float width, float height) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
    }

    public boolean isLastPanel() {
        return lastPanel;
    }
}
