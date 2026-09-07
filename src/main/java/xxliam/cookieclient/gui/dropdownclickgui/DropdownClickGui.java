package xxliam.cookieclient.gui.dropdownclickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.dropdownclickgui.panel.CategoryPanel;
import xxliam.cookieclient.gui.dropdownclickgui.panel.property.impl.BindPropertyPanel;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.impl.render.ClickGui;
import xxliam.cookieclient.modules.impl.render.hud.ModuleList;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.render.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Opal 风格下拉 ClickGUI：屏幕顶部横向一排分类标签，点开向下拉出模块 / 设置树。
 * <p>
 * 移植自 OpenOpal {@code wtf.opal.client.screen.click.dropdown.DropdownClickGUI}。
 * <ul>
 *     <li>布局：分类条 y=25，宽 110、高 20、间距 10，整排水平居中（opal 原值）。</li>
 *     <li>分类条可拖动（受 ClickGui「Allow drag」钳制，不出屏）。</li>
 *     <li>按住 TAB 显示模块键名；绑定走 zen 交互——展开模块后在底部 Bind 行左键进入监听。</li>
 *     <li>关闭动画：Esc / 右 Shift（ClickGui 键）/ 在 GUI 内关掉 ClickGui 模块触发。
 *         Screen 在 onClose 时立即卸载（游戏输入即刻恢复），收拢动画转交静态覆盖层
 *         {@link #renderClosingOverlay}（由 GuiMixin HUD 钩子驱动）继续播放，播完自动停止。</li>
 * </ul>
 * 全部渲染走 cookie Renderer/CustomFont/动画体系，视觉参数照搬 opal。
 */
public class DropdownClickGui extends Screen {

    private static final float CATEGORY_Y = 25.0f;
    private static final float CATEGORY_WIDTH = 110.0f;
    private static final float CATEGORY_SPACING = 10.0f;
    private static final float CATEGORY_HEIGHT = 20.0f;

    /** 右下角「E」按钮尺寸 / 距屏缘留白（与 zen 一致：26px、12px；样式为 opal 体系）。 */
    private static final float BTN_SIZE = 26.0f;
    private static final float BTN_MARGIN = 12.0f;

    private final List<CategoryPanel> categoryPanels = new ArrayList<>();

    /** TAB 是否按住（显示各模块绑定键名）。 */
    public static boolean displayingBinds;

    private boolean closing;

    /** 折叠态（E 按钮）：true=面板收起、仅 ModuleList 拖动可交互（与 zen 的 hidden 语义一致）。 */
    private boolean hidden;

    // ---- ModuleList 布局编辑（仅 hidden 折叠态）：按住列表包围框拖动整列（与 zen 同款） ----
    private boolean moduleDragActive;
    private float moduleDragStartX;
    private float moduleDragStartY;
    private float moduleDragBaseOffsetX;
    private float moduleDragBaseOffsetY;

    /** 关闭覆盖层：Screen 卸载后继续播放收拢动画的面板（静态，与 Screen 生命周期解耦）。 */
    private static List<CategoryPanel> closingPanels;
    private static boolean closingOverlayActive;

    public DropdownClickGui() {
        super(Component.literal("Cookie Client Dropdown ClickGUI"));
        int index = 0;
        for (Category category : Category.values()) {
            categoryPanels.add(new CategoryPanel(category, index));
            index++;
        }
    }

    @Override
    protected void init() {
        categoryPanels.forEach(CategoryPanel::init);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        displayingBinds = isKeyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
        boolean allowDrag = ClickGui.isAllowDrag();

        final int categoryAmount = categoryPanels.size();
        final float totalWidth = categoryAmount * CATEGORY_WIDTH + (categoryAmount - 1) * CATEGORY_SPACING;
        final float startX = (mc.getWindow().getGuiScaledWidth() - totalWidth) / 2.0f;
        for (int i = 0; i < categoryAmount; i++) {
            CategoryPanel panel = categoryPanels.get(i);
            final float x = startX + i * (CATEGORY_WIDTH + CATEGORY_SPACING);
            panel.setBasePosition(x, CATEGORY_Y);
            panel.setDraggingAllowed(allowDrag);
            // opal 同款：每帧把含拖动偏移的最终坐标 + 分类条尺寸写回面板（width/height 参与命中与绘制）
            panel.setDimensions(x + panel.getDragOffsetX(), CATEGORY_Y + panel.getDragOffsetY(),
                    CATEGORY_WIDTH, CATEGORY_HEIGHT);
            panel.render(guiGraphics, mouseX, mouseY, delta, 1.0f);
        }
        renderToggleButton(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右下角「E」按钮：折叠 / 展开面板（zen 同款交互，任何按键都行，取左键）
        if (isButtonHit(mouseX, mouseY)) {
            if (button == 0) {
                toggleHidden();
            }
            return true;
        }
        if (hidden) {
            // 折叠态：左键点在 ModuleList 包围框上开始拖动；其余点击一律吞掉
            if (button == 0 && tryStartModuleDrag(mouseX, mouseY)) {
                return true;
            }
            return true; // 面板已折叠：除按钮外不响应任何点击
        }
        // 绑定监听中：鼠标侧键(3~7)直接绑为鼠标键；其余键不做处理，原样下发面板（zen 同规则）
        BindPropertyPanel listening = BindPropertyPanel.getListening();
        if (listening != null && listening.onMouse(button)) {
            return true;
        }
        boolean allowDrag = ClickGui.isAllowDrag();
        categoryPanels.forEach(panel -> panel.setDraggingAllowed(allowDrag));
        categoryPanels.forEach(panel -> panel.mouseClicked(mouseX, mouseY, button));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (moduleDragActive) {
            moduleDragActive = false;
            return true;
        }
        if (hidden) {
            return false;
        }
        categoryPanels.forEach(panel -> panel.mouseReleased(mouseX, mouseY, button));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // ModuleList 布局拖动（zen 同款）：跟随光标；非自由拖动模式下锁定水平分量（列表贴边上下移动）
        if (moduleDragActive && ModuleList.INSTANCE != null) {
            float nx = moduleDragBaseOffsetX + (float) (mouseX - moduleDragStartX);
            float ny = moduleDragBaseOffsetY + (float) (mouseY - moduleDragStartY);
            if (!ModuleList.FREE_DRAG) {
                nx = moduleDragBaseOffsetX;
            }
            ModuleList.INSTANCE.setDraggedOffset(nx, ny);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 命中 ModuleList 包围框则进入拖动态（记录抓取基准，zen 同款）。 */
    private boolean tryStartModuleDrag(double mouseX, double mouseY) {
        if (ModuleList.INSTANCE == null || !ModuleList.INSTANCE.isFrameHit(mouseX, mouseY)) {
            return false;
        }
        moduleDragActive = true;
        moduleDragStartX = (float) mouseX;
        moduleDragStartY = (float) mouseY;
        moduleDragBaseOffsetX = ModuleList.INSTANCE.getOffsetX();
        moduleDragBaseOffsetY = ModuleList.INSTANCE.getOffsetY();
        return true;
    }

    /** 点击 E 按钮：翻转折叠态并中止可能存在的绑定监听 / ModuleList 拖动（zen 同款）。 */
    private void toggleHidden() {
        hidden = !hidden;
        moduleDragActive = false;
        BindPropertyPanel.clearListening();
        if (hidden) {
            categoryPanels.forEach(CategoryPanel::close);
        } else {
            categoryPanels.forEach(CategoryPanel::reopen);
        }
    }

    /** 面板是否处于折叠态。 */
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hidden) {
            return false;
        }
        categoryPanels.forEach(panel -> panel.mouseScrolled(mouseX, mouseY, 0.0, delta));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 绑定监听中：所有按键交给监听行消费（ESC=解绑本模块绑定，不关闭 GUI），zen 路由一致
        BindPropertyPanel listening = BindPropertyPanel.getListening();
        if (listening != null) {
            return listening.onKey(keyCode);
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            // ESC 交给各面板完成收起动画后统一卸载
            onClose();
            return true;
        }
        int guiKey = ClickGui.INSTANCE != null ? ClickGui.INSTANCE.getKeyBind() : 344;
        if (keyCode == guiKey) {
            onClose();
            return true;
        }
        categoryPanels.forEach(panel -> panel.keyPressed(keyCode));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        categoryPanels.forEach(panel -> panel.charTyped(chr, modifiers));
        return true;
    }

    /**
     * 开始关闭：所有分类进入收拢动画；Screen 本体立即卸载（vanilla 语义上关闭即无输入拦截），
     * 游戏键入 / 移动即刻恢复，动画由 {@link #renderClosingOverlay} 在 HUD 层继续播放。
     */
    @Override
    public void onClose() {
        if (closing) {
            return;
        }
        CookieClient.LOGGER.info("[DropdownClickGui] onClose() start, unloading screen immediately (overlay keeps animation)");
        closing = true;
        BindPropertyPanel.clearListening();
        categoryPanels.forEach(CategoryPanel::close);
        closingPanels = new ArrayList<>(categoryPanels);
        closingOverlayActive = true;
        Minecraft.getInstance().setScreen(null);
        ClickGui.onGuiClosed();
        CookieClient.CONFIG_MANAGER.save();
    }

    /**
     * 关闭覆盖层渲染：由 {@code GuiMixin} 在 HUD 层每帧调用。 Screen 已卸载、游戏输入已恢复，
     * 这里只负责把收拢动画播完；全部分类收拢后自动停止。若期间用户重新打开了 Opal GUI
     * （新 Screen 自带全新面板），覆盖层立即作废，避免新旧两套面板叠画。
     */
    public static void renderClosingOverlay(GuiGraphics guiGraphics, float partialTick) {
        if (!closingOverlayActive || closingPanels == null || closingPanels.isEmpty()) {
            closingOverlayActive = false;
            closingPanels = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DropdownClickGui) {
            closingOverlayActive = false;
            closingPanels = null;
            return;
        }
        int mouseX = 0;
        int mouseY = 0;
        if (mc.getWindow().getScreenWidth() > 0 && mc.getWindow().getScreenHeight() > 0) {
            mouseX = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
            mouseY = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        }
        final int categoryAmount = closingPanels.size();
        final float totalWidth = categoryAmount * CATEGORY_WIDTH + (categoryAmount - 1) * CATEGORY_SPACING;
        final float startX = (mc.getWindow().getGuiScaledWidth() - totalWidth) / 2.0f;
        boolean allFinished = true;
        for (int i = 0; i < categoryAmount; i++) {
            CategoryPanel panel = closingPanels.get(i);
            final float x = startX + i * (CATEGORY_WIDTH + CATEGORY_SPACING);
            panel.setBasePosition(x, CATEGORY_Y);
            panel.setDraggingAllowed(false);
            panel.setDimensions(x, CATEGORY_Y, CATEGORY_WIDTH, CATEGORY_HEIGHT);
            panel.render(guiGraphics, mouseX, mouseY, partialTick, 1.0f);
            if (!panel.isCloseFinished()) {
                allFinished = false;
            }
        }
        if (allFinished) {
            CookieClient.LOGGER.info("[DropdownClickGui] close overlay finished");
            closingOverlayActive = false;
            closingPanels = null;
        }
    }

    /** 供 NewClickGui / Module 侧统一判断当前屏幕。 */
    public boolean isClosing() {
        return closing;
    }

    // ---------------------------------------------------------------------
    // 右下角「E」按钮（位置/大小/功能与 zen 一致，样式为 opal 体系）
    // ---------------------------------------------------------------------

    private float btnCenterX() {
        return this.width - BTN_MARGIN - BTN_SIZE / 2.0f;
    }

    private float btnCenterY() {
        return this.height - BTN_MARGIN - BTN_SIZE / 2.0f;
    }

    /** 命中检测：按钮中心 26+容差 圆内（与 zen 相同的容差判定）。 */
    private boolean isButtonHit(double mouseX, double mouseY) {
        float dx = (float) (mouseX - btnCenterX());
        float dy = (float) (mouseY - btnCenterY());
        float hit = BTN_SIZE / 2.0f + 4.0f;
        return dx * dx + dy * dy <= hit * hit;
    }

    /**
     * opal 样式按钮：分类条同款——后屏模糊（r=5, lod=2.5）+ 0x0F0F0F@85% 圆角方块（r=5），
     * 白色 E 用 productsans-bold 居中；hover 半透明白叠层。zen 版是纯黑圆 + 软阴影，
     * 这里只换皮：几何（26px、右下 12px 留白）与交互完全不变。
     */
    private void renderToggleButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        float cx = btnCenterX();
        float cy = btnCenterY();
        float x = cx - BTN_SIZE / 2.0f;
        float y = cy - BTN_SIZE / 2.0f;
        Renderer.drawScreenBlur(guiGraphics.pose(), x, y, BTN_SIZE, BTN_SIZE, 5.0f, 2.5f);
        Renderer.drawRoundedRect(guiGraphics.pose(), x, y, BTN_SIZE, BTN_SIZE, 5.0f,
                ColorUtil.applyOpacity(0xFF0F0F0F, 0.85f));
        if (isButtonHit(mouseX, mouseY)) {
            Renderer.drawRoundedRect(guiGraphics.pose(), x, y, BTN_SIZE, BTN_SIZE, 5.0f,
                    ColorUtil.fromARGB(255, 255, 255, 14));
        }
        DropdownRender.centered(guiGraphics, FontStore.PRODUCTSANS_BOLD_12, "E", cx, cy,
                ColorUtil.withAlpha(-1, 1.0f));
    }

    private boolean isKeyDown(int key) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
