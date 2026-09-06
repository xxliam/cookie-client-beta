package xxliam.cookieclient.gui.dropdownclickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xxliam.cookieclient.CookieClient;
import xxliam.cookieclient.gui.dropdownclickgui.panel.CategoryPanel;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.impl.render.ClickGui;

import java.util.ArrayList;
import java.util.List;

/**
 * Opal 风格下拉 ClickGUI：屏幕顶部横向一排分类标签，点开向下拉出模块 / 设置树。
 * <p>
 * 移植自 OpenOpal {@code wtf.opal.client.screen.click.dropdown.DropdownClickGUI}。
 * <ul>
 *     <li>布局：分类条 y=25，宽 110、高 20、间距 10，整排水平居中（opal 原值）。</li>
 *     <li>分类条可拖动（受 ClickGui「Allow drag」钳制，不出屏）。</li>
 *     <li>按住 TAB 显示模块键名；中键进入绑定监听。</li>
 *     <li>关闭动画：Esc / 右 Shift（ClickGui 键）/ 在 GUI 内关掉 ClickGui 模块触发；
 *         所有分类收拢后卸载 Screen 并回调 {@link ClickGui#onGuiClosed()}。</li>
 * </ul>
 * 全部渲染走 cookie Renderer/CustomFont/动画体系，视觉参数照搬 opal。
 */
public class DropdownClickGui extends Screen {

    private static final float CATEGORY_Y = 25.0f;
    private static final float CATEGORY_WIDTH = 110.0f;
    private static final float CATEGORY_SPACING = 10.0f;
    private static final float CATEGORY_HEIGHT = 20.0f;

    private final List<CategoryPanel> categoryPanels = new ArrayList<>();

    /** TAB 是否按住（显示各模块绑定键名）；模块自身 Bind 监听态。 */
    public static boolean displayingBinds;
    public static boolean selectingBind;

    private boolean closing;
    private boolean fullyClosed;
    /** 关闭动画开始时刻（兜底超时用）：动画异常时 Screen 不能一直挂着吞输入。 */
    private long closingStartMs;
    /** 关闭硬超时：最长错峰收拢 ≈0.58s，留足余量后强制卸载。 */
    private static final long CLOSE_TIMEOUT_MS = 2000L;

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
        if (fullyClosed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        displayingBinds = isKeyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
        boolean allowDrag = ClickGui.isAllowDrag();

        // 关闭动画完成判定：所有分类都已收拢到位 → 卸载；超时兜底强制卸载（Screen 不卸载会吞掉全部游戏输入）
        boolean closeTimedOut = closing && System.currentTimeMillis() - closingStartMs > CLOSE_TIMEOUT_MS;
        if (closing && (closeTimedOut || categoryPanels.stream().allMatch(CategoryPanel::isCloseFinished))) {
            if (closeTimedOut) {
                CookieClient.LOGGER.warn("[DropdownClickGui] close animation timed out ({}ms), force unloading", CLOSE_TIMEOUT_MS);
            }
            fullyClosed = true;
            CookieClient.LOGGER.info("[DropdownClickGui] close animation finished, unloading");
            Minecraft.getInstance().setScreen(null);
            ClickGui.onGuiClosed();
            CookieClient.CONFIG_MANAGER.save();
            return;
        }

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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean allowDrag = ClickGui.isAllowDrag();
        categoryPanels.forEach(panel -> panel.setDraggingAllowed(allowDrag));
        categoryPanels.forEach(panel -> panel.mouseClicked(mouseX, mouseY, button));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        categoryPanels.forEach(panel -> panel.mouseReleased(mouseX, mouseY, button));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        categoryPanels.forEach(panel -> panel.mouseScrolled(mouseX, mouseY, 0.0, delta));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 绑定监听中：所有按键（ESC=取消绑定）交给持有监听的模块面板消费，不关闭 GUI
        if (selectingBind) {
            categoryPanels.forEach(panel -> panel.keyPressed(keyCode));
            return true;
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

    /** 开始关闭：所有分类进入收起动画。 */
    @Override
    public void onClose() {
        if (closing) {
            return;
        }
        CookieClient.LOGGER.info("[DropdownClickGui] onClose() start");
        closing = true;
        closingStartMs = System.currentTimeMillis();
        selectingBind = false;
        categoryPanels.forEach(CategoryPanel::close);
    }

    /** 供 NewClickGui / Module 侧统一判断当前屏幕。 */
    public boolean isClosing() {
        return closing;
    }

    private boolean isKeyDown(int key) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
