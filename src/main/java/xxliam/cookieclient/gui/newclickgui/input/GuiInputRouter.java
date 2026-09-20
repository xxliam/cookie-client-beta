package xxliam.cookieclient.gui.newclickgui.input;

import xxliam.cookieclient.gui.newclickgui.BindElement;
import xxliam.cookieclient.gui.newclickgui.BooleanSettingElement;
import xxliam.cookieclient.gui.newclickgui.CategoryPanel;
import xxliam.cookieclient.gui.newclickgui.ColorSettingElement;
import xxliam.cookieclient.gui.newclickgui.ModeSettingElement;
import xxliam.cookieclient.gui.newclickgui.ModuleElement;
import xxliam.cookieclient.gui.newclickgui.MultiSelectSettingElement;
import xxliam.cookieclient.gui.newclickgui.NewClickGui;
import xxliam.cookieclient.gui.newclickgui.NumberSettingElement;
import xxliam.cookieclient.gui.newclickgui.SettingElement;
import xxliam.cookieclient.hud.DynamicIsland;
import xxliam.cookieclient.modules.impl.render.ClickGui;
import xxliam.cookieclient.modules.impl.render.hud.ModuleList;

/**
 * ClickGUI 输入路由器：**全部鼠标 / 键盘交互逻辑的唯一入口**。
 * <p>
 * 渲染层（{@code NewClickGui} / {@code CategoryPanel} / {@code ModuleElement} / 各设置控件）
 * 只负责绘制与几何查询（{@code contains*} / {@code *At} 等纯函数），不再处理事件；
 * 事件由本类按「按钮 → 折叠态拖动 → 绑定监听 → 面板 → 模块 → 设置项」的顺序路由，
 * 命中后调用元素暴露的语义化动作方法（{@code toggleModule()} / {@code applySlider()} …）。
 * <p>
 * 拖动状态（面板拖动、滑块、取色盘、ModuleList / 灵动岛布局拖动）也全部集中在本类，
 * 元素不再自己保存拖动态。
 */
public final class GuiInputRouter {

    private final NewClickGui screen;
    private final SliderInputHandler sliders = new SliderInputHandler();
    private final ColorPickerInputHandler pickers = new ColorPickerInputHandler();

    // ---- 分类面板拖动（标题栏抓取） ----
    private CategoryPanel draggingPanel;
    private float panelDragOffsetX;
    private float panelDragOffsetY;

    // ---- ModuleList 布局拖动（仅折叠态）：按住列表包围框拖动整列 ----
    private boolean moduleDragActive;
    private float moduleDragStartX;
    private float moduleDragStartY;
    private float moduleDragBaseOffsetX;
    private float moduleDragBaseOffsetY;

    // ---- 灵动岛布局拖动（仅折叠态）：按住岛包围框沿屏幕中轴线上下拖动 ----
    private boolean islandDragActive;
    private float islandDragStartY;
    private float islandDragBaseOffsetY;

    public GuiInputRouter(NewClickGui screen) {
        this.screen = screen;
    }

    // ---------------------------------------------------------------------
    // 状态查询（供 Screen 复用，例如关闭时中止拖动）
    // ---------------------------------------------------------------------

    /** 当前是否有任何拖动在进行（面板 / 滑块 / 取色盘 / 布局拖动）。 */
    public boolean isAnythingDragging() {
        return draggingPanel != null || sliders.isDragging() || pickers.isDragging()
                || moduleDragActive || islandDragActive;
    }

    /** 中止全部拖动与绑定监听（GUI 关闭 / 折叠时调用）。 */
    public void cancelAll() {
        draggingPanel = null;
        sliders.release();
        pickers.release();
        moduleDragActive = false;
        islandDragActive = false;
        BindInputHandler.clear();
    }

    // ---------------------------------------------------------------------
    // 键盘
    // ---------------------------------------------------------------------

    public boolean keyPressed(int keyCode) {
        // 存在监听态的 Bind 控件时，优先把按键交给它绑定
        if (BindInputHandler.keyPressed(keyCode)) {
            return true;
        }
        // GUI 内按 ClickGui 模块当前绑定的键 = 关闭（与游戏内打开对称；默认右 Shift）
        int guiKey = ClickGui.INSTANCE != null ? ClickGui.INSTANCE.getKeyBind() : 344;
        if (keyCode == guiKey) {
            screen.onClose();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // 鼠标按下
    // ---------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右下角圆形按钮：折叠 / 展开面板（任何按键都行，取左键即可）
        if (screen.isToggleButtonHit(mouseX, mouseY)) {
            if (button == 0) {
                screen.toggleHidden();
                cancelAll();
            }
            return true;
        }
        if (screen.isHidden()) {
            // 折叠态：左键点在各元素包围框上开始拖动；其余点击一律吞掉
            if (button == 0 && (tryStartModuleDrag(mouseX, mouseY) || tryStartIslandDrag(mouseX, mouseY))) {
                return true;
            }
            return true; // 面板已折叠：除按钮外不响应任何点击
        }
        // 存在监听态的 Bind 控件时，侧键按下直接作为鼠标绑定捕获（与 keyPressed 路由按键对称）
        if (BindInputHandler.mousePressed(button)) {
            return true;
        }
        double localX = screen.toLocalX(mouseX);
        double localY = screen.toLocalY(mouseY);
        for (CategoryPanel panel : NewClickGui.getCategoryPanels()) {
            if (clickPanel(panel, localX, localY, button)) {
                NewClickGui.focusedPanel = panel;
                return true;
            }
        }
        return false;
    }

    /** 面板级命中：标题栏 → 开始拖动；模块列表 → 交给模块行；面板内其它位置 → 吞掉。 */
    private boolean clickPanel(CategoryPanel panel, double mouseX, double mouseY, int button) {
        if (!panel.contains(mouseX, mouseY)) {
            return false;
        }
        if (panel.titleContains(mouseX, mouseY)) {
            if (button == 0) {
                draggingPanel = panel;
                panelDragOffsetX = panel.getX() - (float) mouseX;
                panelDragOffsetY = panel.getY() - (float) mouseY;
            }
            return true;
        }
        if (panel.listContains(mouseX, mouseY)) {
            for (ModuleElement moduleElement : panel.getModuleElements()) {
                if (clickModule(moduleElement, mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return true; // 面板已被命中：面板内任何点击都算消费
    }

    /** 模块行命中：标题栏（开关 / 展开）→ 设置区（各设置项 + Bind）。 */
    private boolean clickModule(ModuleElement moduleElement, double mouseX, double mouseY, int button) {
        if (!moduleElement.isWithinBounds(mouseX, mouseY)
                || moduleElement.getParentPanel() != NewClickGui.focusedPanel) {
            return false;
        }
        if (moduleElement.titleContains(mouseX, mouseY)) {
            if (button == 0) {
                moduleElement.toggleModule();
            } else if (button == 1 && !moduleElement.getModule().getSettings().isEmpty()) {
                moduleElement.toggleExpanded();
            }
            return true;
        }
        if (moduleElement.settingsAreaContains(mouseX, mouseY)) {
            for (SettingElement<?> settingElement : moduleElement.getSettingElements()) {
                if (settingElement.isDisplayable() && clickSetting(settingElement, mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (clickBind(moduleElement.getBindElement(), mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    private boolean clickSetting(SettingElement<?> element, double mouseX, double mouseY, int button) {
        if (element instanceof BooleanSettingElement booleanElement) {
            if (button == 0 && booleanElement.contains(mouseX, mouseY)) {
                booleanElement.toggle();
                return true;
            }
        } else if (element instanceof NumberSettingElement numberElement) {
            if (button == 0 && numberElement.sliderContains(mouseX, mouseY)) {
                return sliders.begin(numberElement, mouseX);
            }
        } else if (element instanceof ModeSettingElement modeElement) {
            if (button != 0) {
                return false;
            }
            if (modeElement.dropdownContains(mouseX, mouseY)) {
                modeElement.toggleOpen();
                return true;
            }
            if (modeElement.isOpen()) {
                String mode = modeElement.itemAt(mouseX, mouseY);
                if (mode != null) {
                    modeElement.select(mode);
                    return true;
                }
            }
        } else if (element instanceof MultiSelectSettingElement multiElement) {
            if (button != 0) {
                return false;
            }
            if (multiElement.dropdownContains(mouseX, mouseY)) {
                multiElement.toggleOpen();
                return true;
            }
            if (multiElement.isOpen()) {
                String option = multiElement.itemAt(mouseX, mouseY);
                if (option != null) {
                    multiElement.toggleOption(option);
                    return true;
                }
            }
        } else if (element instanceof ColorSettingElement colorElement) {
            if (button != 0) {
                return false;
            }
            if (colorElement.rowContains(mouseX, mouseY)) {
                colorElement.toggleExpanded();
                return true;
            }
            if (colorElement.isExpanded()) {
                if (colorElement.pickerContains(mouseX, mouseY)) {
                    return pickers.begin(colorElement, ColorPickerInputHandler.Zone.PICKER, mouseX, mouseY);
                }
                if (colorElement.hueContains(mouseX, mouseY)) {
                    return pickers.begin(colorElement, ColorPickerInputHandler.Zone.HUE, mouseX, mouseY);
                }
            }
        }
        return false;
    }

    private boolean clickBind(BindElement bindElement, double mouseX, double mouseY, int button) {
        if (button != 0 || !bindElement.contains(mouseX, mouseY)) {
            return false;
        }
        BindInputHandler.toggle(bindElement);
        return true;
    }

    // ---------------------------------------------------------------------
    // 鼠标松开
    // ---------------------------------------------------------------------

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasLayoutDragging = moduleDragActive || islandDragActive;
        moduleDragActive = false;
        islandDragActive = false;
        sliders.release();
        pickers.release();
        draggingPanel = null;
        if (wasLayoutDragging) {
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // 鼠标拖动
    // ---------------------------------------------------------------------

    public boolean mouseDragged(double mouseX, double mouseY) {
        // 面板拖动（标题栏）：跟随光标
        if (draggingPanel != null) {
            draggingPanel.setX((float) mouseX + panelDragOffsetX);
            draggingPanel.setY((float) mouseY + panelDragOffsetY);
            return true;
        }
        // 滑块 / 取色盘
        if (sliders.drag(mouseX)) {
            return true;
        }
        if (pickers.drag(mouseX, mouseY)) {
            return true;
        }
        // ModuleList 布局拖动：跟随光标并把目标偏移钳制在屏幕内（边框不超出屏幕）。
        // FREE_DRAG=true（自由双向）时沿用下方完整自由移动计算（备用，勿删）；
        // 当前贴边垂直模式（false）仅锁定水平分量 = 拖动基准，列表只能沿屏幕边缘上下移动。
        if (moduleDragActive && ModuleList.INSTANCE != null) {
            float nx = moduleDragBaseOffsetX + (float) (mouseX - moduleDragStartX);
            float ny = moduleDragBaseOffsetY + (float) (mouseY - moduleDragStartY);
            if (!ModuleList.FREE_DRAG) {
                nx = moduleDragBaseOffsetX;
            }
            ModuleList.INSTANCE.setDraggedOffset(nx, ny);
            return true;
        }
        // 灵动岛布局拖动：只取 Y（水平恒居中于屏幕中轴线），钳制在屏幕内
        if (islandDragActive && DynamicIsland.INSTANCE != null) {
            float ny = islandDragBaseOffsetY + (float) (mouseY - islandDragStartY);
            DynamicIsland.INSTANCE.setDraggedOffsetY(ny);
            return true;
        }
        return false;
    }

    /** 命中 ModuleList 包围框则进入拖动态（记录抓取基准）。 */
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

    /** 命中灵动岛包围框则进入拖动态（记录抓取基准；只走 Y = 沿屏幕中轴线上下）。 */
    private boolean tryStartIslandDrag(double mouseX, double mouseY) {
        if (DynamicIsland.INSTANCE == null || !DynamicIsland.INSTANCE.isFrameHit(mouseX, mouseY)) {
            return false;
        }
        islandDragActive = true;
        islandDragStartY = (float) mouseY;
        islandDragBaseOffsetY = DynamicIsland.INSTANCE.getOffsetY();
        return true;
    }

    // ---------------------------------------------------------------------
    // 滚轮
    // ---------------------------------------------------------------------

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (screen.isHidden()) {
            return false;
        }
        double localX = screen.toLocalX(mouseX);
        double localY = screen.toLocalY(mouseY);
        for (CategoryPanel panel : NewClickGui.getCategoryPanels()) {
            if (panel.scrollBy(localX, localY, scrollDelta)) {
                return true;
            }
        }
        return false;
    }
}
