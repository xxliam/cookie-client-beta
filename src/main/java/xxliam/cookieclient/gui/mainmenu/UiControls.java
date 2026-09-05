package xxliam.cookieclient.gui.mainmenu;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Setsuna 独立页共享控件（panel/card/button/disc/brand/TextInput…），照搬 UiControls 数值与行为。
 * <p>
 * SkiaUi 调用已替换为 {@link UiDraw}/{@link MenuText}；键鼠事件走 cookie Screen 语义，
 * TextInput 逻辑（选区/光标/剪贴板）保持原版。
 */
final class UiControls {

    enum Tone {
        NORMAL,
        PRIMARY,
        DANGER
    }

    record Box(float x, float y, float width, float height) {

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        Box inset(float amount) {
            return new Box(x + amount, y + amount, Math.max(0, width - amount * 2),
                    Math.max(0, height - amount * 2));
        }
    }

    static final float RADIUS = 9.0F;
    static final float RADIUS_SMALL = 6.0F;

    static final int TEXT = 0xFFF2F4F8;
    static final int TEXT_MUTED = 0xFF9AA2B0;
    static final int TEXT_FAINT = 0xFF5C6472;

    static final int PANEL = 0xE60C0F16;
    static final int CARD = 0x99141826;
    static final int CARD_HOVER = 0xC81C2231;
    static final int STROKE = 0x16FFFFFF;
    static final int STROKE_STRONG = 0x2CFFFFFF;
    static final int HIGHLIGHT = 0x1EFFFFFF;
    static final int SHADOW = 0x63000000;

    private UiControls() {
    }

    static void shadow(PoseStack poseStack, Box box, float radius) {
        UiDraw.rounded(poseStack, box.x() - 1.0F, box.y() + 3.0F, box.width() + 2.0F,
                box.height() + 4.0F, radius + 1.0F, SHADOW);
    }

    static void surface(PoseStack poseStack, Box box, int fill, int stroke, float radius) {
        UiDraw.rounded(poseStack, box.x(), box.y(), box.width(), box.height(), radius, stroke);
        UiDraw.rounded(poseStack, box.x() + 1, box.y() + 1, Math.max(0, box.width() - 2),
                Math.max(0, box.height() - 2), Math.max(0, radius - 1), fill);
        UiDraw.rounded(poseStack, box.x() + 2, box.y() + 1, Math.max(0, box.width() - 4), 1.0F,
                0.5F, HIGHLIGHT);
    }

    static void card(PoseStack poseStack, Box box, boolean hovered, boolean selected) {
        int accent = UiTheme.accent();
        int fill = selected ? UiTheme.withAlpha(accent, 40)
                : hovered ? CARD_HOVER : CARD;
        int stroke = selected ? UiTheme.withAlpha(accent, 150)
                : hovered ? STROKE_STRONG : STROKE;
        surface(poseStack, box, fill, stroke, RADIUS_SMALL);
    }

    static void disc(PoseStack poseStack, float x, float y, float size, int color) {
        UiDraw.rounded(poseStack, x, y, size, size, size * 0.5F, color);
    }

    static int onAccent(int accent) {
        int red = (accent >> 16) & 0xFF;
        int green = (accent >> 8) & 0xFF;
        int blue = accent & 0xFF;
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
        return luminance > 0.6 ? 0xFF0B0E14 : TEXT;
    }

    static void brand(PoseStack poseStack, String text, float x, float top, float height,
                      int color, float size, float tracking) {
        MenuText.boldTextTracked(poseStack, text, x, top, height, color, size, tracking);
    }

    static float brandWidth(String text, float size, float tracking) {
        return MenuText.boldTextWidthTracked(text, size, tracking);
    }

    static void panel(PoseStack poseStack, Box box) {
        shadow(poseStack, box, RADIUS);
        surface(poseStack, box, PANEL, STROKE_STRONG, RADIUS);
    }

    static void section(PoseStack poseStack, Box box) {
        surface(poseStack, box, CARD, STROKE, RADIUS_SMALL);
    }

    static void button(PoseStack poseStack, Box box, String label,
                       boolean hovered, boolean enabled, Tone tone) {
        int accent = UiTheme.accent();
        int fill;
        int stroke;
        int foreground;
        if (!enabled) {
            fill = 0x120E1420;
            stroke = STROKE;
            foreground = TEXT_FAINT;
        } else if (tone == Tone.PRIMARY) {
            fill = hovered ? accent : UiTheme.withAlpha(accent, 205);
            stroke = accent;
            foreground = onAccent(accent);
        } else if (tone == Tone.DANGER) {
            fill = hovered ? 0xE6E0504C : 0x33E0504C;
            stroke = hovered ? 0xFFE0504C : 0x66E0504C;
            foreground = hovered ? TEXT : 0xFFF0A5A2;
        } else {
            fill = hovered ? CARD_HOVER : CARD;
            stroke = hovered ? UiTheme.withAlpha(accent, 130) : STROKE_STRONG;
            foreground = TEXT;
        }

        UiDraw.rounded(poseStack, box.x, box.y, box.width, box.height, RADIUS_SMALL, stroke);
        UiDraw.rounded(poseStack, box.x + 1, box.y + 1,
                Math.max(0, box.width - 2), Math.max(0, box.height - 2),
                Math.max(0, RADIUS_SMALL - 1), fill);
        if (enabled && tone != Tone.PRIMARY) {
            UiDraw.rounded(poseStack, box.x + 2, box.y + 1,
                    Math.max(0, box.width - 4), 1.0F, 0.5F, HIGHLIGHT);
        }
        centeredText(poseStack, label, box, foreground, true);
    }

    static void centeredText(PoseStack poseStack, String text, Box box, int color, boolean bold) {
        String value = ellipsize(text, Math.max(0, box.width - 8), bold);
        float textWidth = bold ? MenuText.boldTextWidth(value, 9.0F) : MenuText.textWidth(value, 9.0F);
        float x = box.x + Math.max(0, (box.width - textWidth) * 0.5F);
        if (bold) {
            MenuText.boldText(poseStack, value, x, box.y, box.height, color, 9.0F);
        } else {
            MenuText.text(poseStack, value, x, box.y, box.height, color, 9.0F);
        }
    }

    static String ellipsize(String text, float maxWidth) {
        return ellipsize(text, maxWidth, false);
    }

    static String ellipsize(String text, float maxWidth, boolean bold) {
        String value = Objects.requireNonNullElse(text, "");
        if (maxWidth <= 0) {
            return "";
        }
        if (measure(value, bold) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int end = value.length();
        while (end > 0 && measure(value.substring(0, end) + suffix, bold) > maxWidth) {
            end = value.offsetByCodePoints(end, -1);
        }
        return end == 0 && measure(suffix, bold) > maxWidth ? "" : value.substring(0, end) + suffix;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float measure(String text, boolean bold) {
        return bold ? MenuText.boldTextWidth(text, 9.0F) : MenuText.textWidth(text, 9.0F);
    }

    /** 单行文本输入框（选区/光标/剪贴板，行为照搬 Setsuna TextInput）。 */
    static final class TextInput {

        private final int maxLength;
        private final boolean password;
        private final IntPredicate characterFilter;
        private final StringBuilder value = new StringBuilder();

        private Box bounds = new Box(0, 0, 0, 0);
        private String placeholder = "";
        private int cursor;
        private int anchor;
        private int viewStart;
        private boolean focused;

        TextInput(int maxLength) {
            this(maxLength, false, codePoint -> true);
        }

        TextInput(int maxLength, boolean password) {
            this(maxLength, password, codePoint -> true);
        }

        TextInput(int maxLength, boolean password, IntPredicate characterFilter) {
            this.maxLength = Math.max(0, maxLength);
            this.password = password;
            this.characterFilter = characterFilter == null ? codePoint -> true : characterFilter;
        }

        void setBounds(Box bounds) {
            this.bounds = bounds;
        }

        Box bounds() {
            return bounds;
        }

        void setPlaceholder(String placeholder) {
            this.placeholder = Objects.requireNonNullElse(placeholder, "");
        }

        String text() {
            return value.toString();
        }

        void setText(String text) {
            value.setLength(0);
            cursor = 0;
            anchor = 0;
            viewStart = 0;
            insertFiltered(Objects.requireNonNullElse(text, ""));
            cursor = value.length();
            anchor = cursor;
        }

        void clear() {
            value.setLength(0);
            cursor = 0;
            anchor = 0;
            viewStart = 0;
        }

        boolean isFocused() {
            return focused;
        }

        void focus() {
            focused = true;
        }

        void blur() {
            focused = false;
            anchor = cursor;
        }

        boolean click(double mouseX, double mouseY, boolean doubleClick) {
            if (!bounds.contains(mouseX, mouseY)) {
                blur();
                return false;
            }
            focused = true;
            if (doubleClick) {
                cursor = value.length();
                anchor = 0;
                return true;
            }

            String shown = displayValue();
            float relativeX = (float) mouseX - bounds.x - 5;
            int closest = viewStart;
            float closestDistance = Math.abs(relativeX);
            for (int index = viewStart; index <= shown.length(); ) {
                float candidateX = inputTextWidth(shown.substring(viewStart, index));
                float distance = Math.abs(relativeX - candidateX);
                if (distance < closestDistance) {
                    closest = index;
                    closestDistance = distance;
                }
                if (candidateX > bounds.width - 2 && candidateX > relativeX) {
                    break;
                }
                if (index == shown.length()) {
                    break;
                }
                index = nextIndex(shown, index);
            }
            cursor = Math.min(closest, value.length());
            anchor = cursor;
            return true;
        }

        boolean keyPressed(int keyCode, int modifiers, Minecraft minecraft) {
            if (!focused) {
                return false;
            }
            boolean control = (modifiers & 2) != 0 || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                blur();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_A && control) {
                anchor = 0;
                cursor = value.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C && control) {
                if (hasSelection()) {
                    minecraft.keyboardHandler.setClipboard(selectedText());
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X && control) {
                if (hasSelection()) {
                    minecraft.keyboardHandler.setClipboard(selectedText());
                    deleteSelection();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && control) {
                replaceSelection(minecraft.keyboardHandler.getClipboard());
                return true;
            }

            boolean selecting = shift;
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                moveCursor(previousIndex(value, cursor), selecting);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                moveCursor(nextIndex(value, cursor), selecting);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                moveCursor(0, selecting);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                moveCursor(value.length(), selecting);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!deleteSelection() && cursor > 0) {
                    int previous = previousIndex(value, cursor);
                    value.delete(previous, cursor);
                    cursor = previous;
                    anchor = cursor;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (!deleteSelection() && cursor < value.length()) {
                    value.delete(cursor, nextIndex(value, cursor));
                    anchor = cursor;
                }
                return true;
            }
            return false;
        }

        boolean charTyped(char typedChar, int modifiers) {
            if (!focused || typedChar == 0) {
                return false;
            }
            if (typedChar < 32 || typedChar == 127) {
                return false;
            }
            int codePoint = typedChar;
            if (!characterFilter.test(codePoint)) {
                return false;
            }
            replaceSelection(String.valueOf((char) codePoint));
            return true;
        }

        void draw(PoseStack poseStack, int mouseX, int mouseY) {
            draw(poseStack, mouseX, mouseY, UiTheme.accent(), CARD, STROKE_STRONG);
        }

        void draw(PoseStack poseStack, int mouseX, int mouseY, int accent, int fill, int border) {
            boolean hovered = bounds.contains(mouseX, mouseY);
            int outline = focused ? accent : hovered ? UiTheme.TEXT_FAINT : border;
            UiDraw.rounded(poseStack, bounds.x, bounds.y, bounds.width, bounds.height,
                    UiTheme.RADIUS_SMALL, outline);
            UiDraw.rounded(poseStack, bounds.x + 1, bounds.y + 1,
                    Math.max(0, bounds.width - 2), Math.max(0, bounds.height - 2),
                    Math.max(0, UiTheme.RADIUS_SMALL - 1), fill);

            if (value.isEmpty() && !focused) {
                MenuText.text(poseStack,
                        ellipsize(placeholder, Math.max(0, bounds.width - 10)),
                        bounds.x + 5, bounds.y, bounds.height, UiTheme.TEXT_FAINT, 9.0F);
                return;
            }

            String shown = displayValue();
            float available = Math.max(0, bounds.width - 10);
            keepCursorVisible(shown, available);
            int visibleEnd = viewStart;
            while (visibleEnd < shown.length()) {
                int next = nextIndex(shown, visibleEnd);
                if (inputTextWidth(shown.substring(viewStart, next)) > available) {
                    break;
                }
                visibleEnd = next;
            }

            if (hasSelection()) {
                int selectionStart = Math.max(Math.min(cursor, anchor), viewStart);
                int selectionEnd = Math.min(Math.max(cursor, anchor), visibleEnd);
                if (selectionStart < selectionEnd) {
                    float selectionX = bounds.x + 5 + inputTextWidth(shown.substring(viewStart, selectionStart));
                    float selectionWidth = inputTextWidth(shown.substring(selectionStart, selectionEnd));
                    UiDraw.fill(poseStack, selectionX, bounds.y + 4,
                            selectionWidth, Math.max(1, bounds.height - 8),
                            UiTheme.withAlpha(accent, 48));
                }
            }

            MenuText.text(poseStack, shown.substring(viewStart, visibleEnd),
                    bounds.x + 5, bounds.y, bounds.height, UiTheme.TEXT, 9.0F);
            if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int visibleCursor = Math.max(viewStart, Math.min(cursor, visibleEnd));
                float cursorX = bounds.x + 5 + inputTextWidth(shown.substring(viewStart, visibleCursor));
                UiDraw.fill(poseStack, cursorX, bounds.y + 4, 1,
                        Math.max(1, bounds.height - 8), UiTheme.TEXT);
            }
        }

        private void keepCursorVisible(String shown, float available) {
            viewStart = Math.max(0, Math.min(viewStart, Math.min(cursor, shown.length())));
            if (inputTextWidth(shown.substring(viewStart, cursor)) > available) {
                int candidate = cursor;
                while (candidate > 0) {
                    int previous = previousIndex(shown, candidate);
                    if (inputTextWidth(shown.substring(previous, cursor)) > available) {
                        break;
                    }
                    candidate = previous;
                }
                viewStart = candidate;
            }
            while (cursor == shown.length() && viewStart > 0) {
                int previous = previousIndex(shown, viewStart);
                if (inputTextWidth(shown.substring(previous, cursor)) > available) {
                    break;
                }
                viewStart = previous;
            }
        }

        private String displayValue() {
            return password ? "*".repeat(value.length()) : value.toString();
        }

        private static float inputTextWidth(String text) {
            return MenuText.textWidth(text, 9.0F);
        }

        private void replaceSelection(String text) {
            deleteSelection();
            insertFiltered(Objects.requireNonNullElse(text, ""));
            anchor = cursor;
        }

        private void insertFiltered(String text) {
            int remaining = maxLength - value.length();
            if (remaining <= 0 || text.isEmpty()) {
                return;
            }
            StringBuilder accepted = new StringBuilder(Math.min(remaining, text.length()));
            text.codePoints().forEach(codePoint -> {
                if (accepted.length() >= remaining || Character.isISOControl(codePoint)
                        || !characterFilter.test(codePoint)) {
                    return;
                }
                String candidate = new String(Character.toChars(codePoint));
                if (accepted.length() + candidate.length() <= remaining) {
                    accepted.append(candidate);
                }
            });
            value.insert(cursor, accepted);
            cursor += accepted.length();
        }

        private boolean hasSelection() {
            return cursor != anchor;
        }

        private String selectedText() {
            return value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor));
        }

        private boolean deleteSelection() {
            if (!hasSelection()) {
                return false;
            }
            int start = Math.min(cursor, anchor);
            int end = Math.max(cursor, anchor);
            value.delete(start, end);
            cursor = start;
            anchor = start;
            return true;
        }

        private void moveCursor(int newCursor, boolean selecting) {
            cursor = Math.max(0, Math.min(value.length(), newCursor));
            if (!selecting) {
                anchor = cursor;
            }
        }

        private static int previousIndex(CharSequence text, int index) {
            if (index <= 0) {
                return 0;
            }
            char previous = text.charAt(index - 1);
            return Character.isLowSurrogate(previous)
                    && index > 1
                    && Character.isHighSurrogate(text.charAt(index - 2))
                    ? index - 2
                    : index - 1;
        }

        private static int nextIndex(CharSequence text, int index) {
            if (index >= text.length()) {
                return text.length();
            }
            char current = text.charAt(index);
            return Character.isHighSurrogate(current)
                    && index + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(index + 1))
                    ? index + 2
                    : index + 1;
        }
    }
}
