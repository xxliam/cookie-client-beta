package xxliam.cookieclient.gui.newclickgui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import xxliam.cookieclient.gui.newclickgui.input.BindInputHandler;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.utils.misc.CursorUtil;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ThemeHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * 按键绑定控件：展开模块底部的一行「Bind」按钮。
 * <p>
 * 纯视图：绘制 + 命中查询（{@link #contains}）+ 语义化动作（{@link #bind(int)}）。
 * 监听态（谁在等按键）由 {@code input.BindInputHandler} 持有，进入 / 退出监听、按键与鼠标侧键
 * 的消费全部由 {@code input.GuiInputRouter} 与该处理器负责；本类只按
 * {@link BindInputHandler#isListening} 决定右侧显示 {@code ...} 还是当前键名。
 */
public class BindElement extends UIElement {

    /** TAB 悬浮层键名的最大字符数（超出即截断，保证右侧徽标不会挤到居中的模块名）。 */
    public static final int MAX_KEY_NAME_LENGTH = 4;

    private final CategoryPanel parentPanel;
    private final Module module;

    public BindElement(CategoryPanel parentPanel, Module module) {
        this.parentPanel = parentPanel;
        this.module = module;
    }

    @Override
    public float getHeight() {
        return 18.0f;
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float nameY = y + (getHeight() - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f;
        // "Bind" 标签靠左对齐（与其它设置项一致）
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, "Bind", x + 6.0f, nameY, ThemeHelper.foreground(alpha * 0.8f));

        // 右侧显示当前绑定的按键名（监听态显示 "..."，未绑定显示 "None"）
        String display;
        int color;
        if (BindInputHandler.isListening(this)) {
            display = "...";
            color = CategoryPanel.ACCENT_COLOR;
        } else if (module.getKeyBind() <= 0) {
            display = "None";
            color = ThemeHelper.foreground(0.5f);
        } else {
            display = getKeyName(module.getKeyBind());
            color = ThemeHelper.foreground(1.0f);
        }
        float displayX = x + 120.0f - FontStore.AXIFORMA_BOLD_13.getStringWidth(display) - 6.0f;
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, display, displayX, nameY, ColorUtil.withAlpha(color, alpha));
    }

    // ------------------------------------------------------------------
    // 几何查询 + 动作（供 GuiInputRouter / BindInputHandler 调用）
    // ------------------------------------------------------------------

    /** 命中查询：整行 Bind 按钮。 */
    @Override
    public boolean contains(double mouseX, double mouseY) {
        return CursorUtil.isInBounds((float) mouseX, (float) mouseY, x, y, 120.0f, getHeight());
    }

    /** 写入绑定键码（监听态收到按键 / 鼠标侧键时由处理器调用）。 */
    public void bind(int key) {
        module.setKeyBind(key);
    }

    /** 该 GLFW 鼠标按钮码是否可被绑定（GLFW_MOUSE_BUTTON_4=3 .. GLFW_MOUSE_BUTTON_LAST=7）。 */
    public static boolean isBindableMouseButton(int button) {
        return button >= GLFW.GLFW_MOUSE_BUTTON_4 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST;
    }

    public Module getModule() {
        return module;
    }

    /** 将 GLFW 键码转换为可读的按键名（如 {@code R}、{@code Space}、{@code Mouse 4}）。 */
    public static String getKeyName(int key) {
        // 额外鼠标键：GLFW 码 3~7 对应物理 Mouse 4~8
        if (isBindableMouseButton(key)) {
            return "Mouse " + (key - GLFW.GLFW_MOUSE_BUTTON_1 + 1);
        }
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    /**
     * 长键名的缩写对照表：键 = GLFW 键码，值 = 短名。
     * <p>
     * 只收录「显示名较长」的按键（修饰键 / 功能键 / 方向键 / 小键盘），字母数字与 F1~F25
     * 本来就只有 1~3 字符，天然不需要缩写。所有取值**不超过 {@link #MAX_KEY_NAME_LENGTH} 个字符**。
     * <p>
     * <b>以键码而非显示名字符串作键</b>，是为了不受游戏语言影响：中文语言包下
     * {@link #getKeyName(int)} 会返回「左Shift」「大写锁定」这类本地化名，按字符串对照必然失配。
     */
    private static final Map<Integer, String> SHORT_KEY_NAMES = buildShortKeyNames();

    private static Map<Integer, String> buildShortKeyNames() {
        Map<Integer, String> map = new HashMap<>();
        map.put(GLFW.GLFW_KEY_ESCAPE, "ESC");
        map.put(GLFW.GLFW_KEY_ENTER, "ENTR");
        map.put(GLFW.GLFW_KEY_SPACE, "SPC");
        map.put(GLFW.GLFW_KEY_PAUSE, "PAUS");
        map.put(GLFW.GLFW_KEY_BACKSPACE, "BKSP");
        map.put(GLFW.GLFW_KEY_INSERT, "INS");
        map.put(GLFW.GLFW_KEY_DELETE, "DEL");
        map.put(GLFW.GLFW_KEY_UP, "UP");
        map.put(GLFW.GLFW_KEY_DOWN, "DOWN");
        map.put(GLFW.GLFW_KEY_LEFT, "LEFT");
        map.put(GLFW.GLFW_KEY_RIGHT, "RGHT");
        map.put(GLFW.GLFW_KEY_PAGE_UP, "PGUP");
        map.put(GLFW.GLFW_KEY_PAGE_DOWN, "PGDN");
        map.put(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS");
        map.put(GLFW.GLFW_KEY_SCROLL_LOCK, "SCRL");
        map.put(GLFW.GLFW_KEY_NUM_LOCK, "NUM");
        map.put(GLFW.GLFW_KEY_PRINT_SCREEN, "PRNT");
        // 修饰键统一「L/R + 3 字符」：Shift/Ctrl/Alt/Win 各缩写 3 字符，合起来正好 4
        map.put(GLFW.GLFW_KEY_LEFT_SHIFT, "LSHF");
        map.put(GLFW.GLFW_KEY_RIGHT_SHIFT, "RSHF");
        map.put(GLFW.GLFW_KEY_LEFT_CONTROL, "LCTL");
        map.put(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTL");
        map.put(GLFW.GLFW_KEY_LEFT_ALT, "LALT");
        map.put(GLFW.GLFW_KEY_RIGHT_ALT, "RALT");
        map.put(GLFW.GLFW_KEY_LEFT_SUPER, "LWIN");
        map.put(GLFW.GLFW_KEY_RIGHT_SUPER, "RWIN");
        map.put(GLFW.GLFW_KEY_WORLD_1, "W1");
        map.put(GLFW.GLFW_KEY_WORLD_2, "W2");
        // 小键盘统一 NUM 前缀（数字/符号各占 1 字符，4 字符内刚好放得下）
        map.put(GLFW.GLFW_KEY_KP_DECIMAL, "NUM.");
        map.put(GLFW.GLFW_KEY_KP_DIVIDE, "NUM/");
        map.put(GLFW.GLFW_KEY_KP_MULTIPLY, "NUM*");
        map.put(GLFW.GLFW_KEY_KP_SUBTRACT, "NUM-");
        map.put(GLFW.GLFW_KEY_KP_ADD, "NUM+");
        map.put(GLFW.GLFW_KEY_KP_ENTER, "NENT");
        map.put(GLFW.GLFW_KEY_KP_EQUAL, "NUM=");
        // 小键盘数字：KP_0~KP_9 在 GLFW 里是连续码位
        for (int i = 0; i <= 9; i++) {
            map.put(GLFW.GLFW_KEY_KP_0 + i, "NUM" + i);
        }
        return Map.copyOf(map);
    }

    /**
     * TAB 悬浮层（{@code ModuleElement}）用的键名：长键名取缩写（{@code Left Control → LCTL}、
     * {@code Mouse 4 → M4}），短键名（字母 / 数字 / F 键 / Space …）沿用全名。
     * <p>
     * <b>返回长度恒 ≤ {@link #MAX_KEY_NAME_LENGTH}</b>：对照表已按上限设计，末尾再兜一道截断，
     * 覆盖「表外按键在其它语言包下显示名偏长」的情况（如未翻译的长名）。
     * <p>
     * 之所以只在 TAB 层用缩写：该层模块名居中、键名右对齐，行宽仅 120px，长名会与模块名重叠；
     * Bind 行空间充裕，仍显示 {@link #getKeyName(int)} 的全名便于确认绑定。
     */
    public static String getShortKeyName(int key) {
        String name;
        if (isBindableMouseButton(key)) {
            name = "M" + (key - GLFW.GLFW_MOUSE_BUTTON_1 + 1);
        } else {
            String shortName = SHORT_KEY_NAMES.get(key);
            name = shortName != null ? shortName : getKeyName(key);
        }
        return name.length() <= MAX_KEY_NAME_LENGTH ? name : name.substring(0, MAX_KEY_NAME_LENGTH);
    }
}
