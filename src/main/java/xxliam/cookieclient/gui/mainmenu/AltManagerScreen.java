package xxliam.cookieclient.gui.mainmenu;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.List;

/**
 * 账户选择页（复刻 Setsuna AltManagerScreen 高保真移植）：可滚动账户卡片网格 + 底部操作栏。
 * <p>
 * 交互：单击选中、双击登录、右键删除、滚轮翻页、Enter 提交离线名/令牌、Tab 切输入框。
 */
public final class AltManagerScreen extends Screen {

    private static final float CARD_HEIGHT = 46.0F;
    private static final float CARD_GAP = 8.0F;

    private final Screen parent;
    private final UiControls.TextInput offlineName = new UiControls.TextInput(
            16,
            false,
            codePoint -> codePoint == '_' || codePoint >= '0' && codePoint <= '9'
                    || codePoint >= 'A' && codePoint <= 'Z'
                    || codePoint >= 'a' && codePoint <= 'z'
    );
    private final UiControls.TextInput minecraftToken = new UiControls.TextInput(4096, true);

    private List<Alt> accounts = List.of();
    private Alt selected;
    private int firstVisible;
    private boolean loginRunning;
    private String status = "Select an account";
    private boolean statusError;

    private int mouseX;
    private int mouseY;
    private long transitionStartedAt = System.nanoTime();
    private long lastClickAt;
    private double lastClickX;
    private double lastClickY;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Alt Manager"));
        this.parent = parent;
        offlineName.setPlaceholder("Offline username");
        minecraftToken.setPlaceholder("Minecraft access token");
    }

    @Override
    public void added() {
        super.added();
        transitionStartedAt = System.nanoTime();
    }

    @Override
    protected void init() {
        AltManager.INSTANCE.load();
        refreshAccounts();
        updateInputBounds(layout());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        PoseStack poseStack = guiGraphics.pose();
        Layout layout = layout();
        updateInputBounds(layout);
        clampFirstVisible(layout);
        BackdropRenderer.draw(poseStack, width, height, 160);
        UiControls.panel(poseStack, layout.workspace);
        drawTopBar(poseStack, layout);
        drawGrid(poseStack, layout);
        drawActionBar(poseStack, layout);
        drawStatusBar(poseStack, layout);

        int transitionAlpha = PageTransition.overlayAlpha(transitionStartedAt);
        if (transitionAlpha > 0) {
            UiDraw.fill(poseStack, 0.0F, 0.0F, width, height, transitionAlpha << 24);
        }
    }

    private void drawTopBar(PoseStack poseStack, Layout layout) {
        float titleX = layout.topBar.x() + 14.0F;
        UiControls.brand(poseStack, "ALT MANAGER", titleX, layout.topBar.y() + 8.0F, 20.0F,
                UiControls.TEXT, 13.0F, 1.4F);
        float titleWidth = UiControls.brandWidth("ALT MANAGER", 13.0F, 1.4F);
        MenuText.text(poseStack, accounts.size() + " accounts", titleX + titleWidth + 12.0F,
                layout.topBar.y() + 12.0F, 12.0F, UiControls.TEXT_FAINT, 7.0F);
        UiControls.button(poseStack, layout.back, "Back", layout.back.contains(mouseX, mouseY), true,
                UiControls.Tone.NORMAL);
    }

    private void drawGrid(PoseStack poseStack, Layout layout) {
        UiControls.Box grid = layout.grid;
        UiDraw.rounded(poseStack, grid.x(), grid.y(), grid.width(), grid.height(),
                UiControls.RADIUS_SMALL, 0x33000000);

        if (accounts.isEmpty()) {
            UiControls.centeredText(poseStack, "No saved accounts",
                    new UiControls.Box(grid.x() + 8, grid.y(), grid.width() - 16, grid.height()),
                    UiTheme.TEXT_FAINT, false);
            return;
        }

        Alt current = AltManager.INSTANCE.getLastAlt().orElse(null);
        int accent = UiTheme.accent();
        int visible = visibleCards(layout);
        for (int slot = 0; slot < visible && firstVisible + slot < accounts.size(); slot++) {
            Alt alt = accounts.get(firstVisible + slot);
            UiControls.Box card = cardBox(layout, slot);
            boolean isSelected = sameAccount(alt, selected);
            boolean isCurrent = sameAccount(alt, current)
                    || alt.getUsername().equalsIgnoreCase(AltManager.INSTANCE.getCurrentUsername());
            boolean hovered = card.contains(mouseX, mouseY);
            UiControls.card(poseStack, card, hovered, isSelected);

            float discSize = 26.0F;
            float discX = card.x() + 8.0F;
            float discY = card.y() + (card.height() - discSize) * 0.5F;
            int discFill;
            int discText;
            if (isSelected) {
                discFill = accent;
                discText = UiControls.onAccent(accent);
            } else if (isCurrent) {
                discFill = UiTheme.withAlpha(accent, 150);
                discText = UiControls.TEXT;
            } else {
                discFill = UiControls.CARD_HOVER;
                discText = UiControls.TEXT_MUTED;
            }
            UiControls.disc(poseStack, discX, discY, discSize, discFill);
            UiControls.centeredText(poseStack, firstLetter(alt.getUsername()),
                    new UiControls.Box(discX, discY, discSize, discSize), discText, true);

            float textX = discX + discSize + 10.0F;
            float textWidth = card.x() + card.width() - textX - 10.0F;
            MenuText.boldText(poseStack, UiControls.ellipsize(alt.getUsername(), textWidth, true),
                    textX, card.y() + 9, 15, UiControls.TEXT);
            MenuText.text(poseStack, UiControls.ellipsize(accountType(alt), textWidth),
                    textX, card.y() + 25, 12, isCurrent ? accent : UiControls.TEXT_FAINT);

            if (isCurrent) {
                UiControls.disc(poseStack, card.x() + card.width() - 13.0F, card.y() + 8.0F,
                        5.0F, accent);
            }
        }
    }

    private void drawActionBar(PoseStack poseStack, Layout layout) {
        MenuText.boldText(poseStack, "ADD OFFLINE", layout.offlineName.x(),
                layout.offlineName.y() - 13, 11, UiControls.TEXT_FAINT, 7);
        offlineName.draw(poseStack, mouseX, mouseY);
        UiControls.button(poseStack, layout.add, "Add", layout.add.contains(mouseX, mouseY),
                !loginRunning, UiControls.Tone.NORMAL);

        MenuText.boldText(poseStack, "DIRECT TOKEN", layout.minecraftToken.x(),
                layout.minecraftToken.y() - 13, 11, UiControls.TEXT_FAINT, 7);
        minecraftToken.draw(poseStack, mouseX, mouseY);
        UiControls.button(poseStack, layout.tokenLogin, "Use",
                layout.tokenLogin.contains(mouseX, mouseY), !loginRunning, UiControls.Tone.NORMAL);

        UiControls.button(poseStack, layout.microsoft,
                loginRunning ? "Microsoft login in progress..." : "Microsoft device login",
                layout.microsoft.contains(mouseX, mouseY), !loginRunning, UiControls.Tone.PRIMARY);
        UiControls.button(poseStack, layout.login, "Use selected",
                layout.login.contains(mouseX, mouseY),
                selected != null && !loginRunning, UiControls.Tone.NORMAL);
        UiControls.button(poseStack, layout.remove, "Remove",
                layout.remove.contains(mouseX, mouseY),
                selected != null && !loginRunning, UiControls.Tone.DANGER);
    }

    private void drawStatusBar(PoseStack poseStack, Layout layout) {
        UiDraw.fill(poseStack, layout.statusBar.x(), layout.statusBar.y(),
                layout.statusBar.width(), layout.statusBar.height(), UiTheme.argb(150, 8, 12, 14));
        int color = statusError ? UiTheme.DANGER : loginRunning ? UiTheme.INFO : UiTheme.TEXT_MUTED;
        UiDraw.rounded(poseStack, layout.statusBar.x() + 10, layout.statusBar.y() + 9,
                5, 5, 2.5F, color);
        MenuText.text(poseStack, UiControls.ellipsize(status, layout.statusBar.width() - 80),
                layout.statusBar.x() + 21, layout.statusBar.y() + 4,
                layout.statusBar.height() - 4, color, 8);
        String mode = loginRunning ? "WORKING" : statusError ? "FAILED" : "READY";
        float modeWidth = MenuText.boldTextWidth(mode, 7);
        MenuText.boldText(poseStack, mode,
                layout.statusBar.x() + layout.statusBar.width() - modeWidth - 10,
                layout.statusBar.y() + 4, layout.statusBar.height() - 4, color, 7);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean doubleClick = false;
        long now = System.currentTimeMillis();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double distance = Math.hypot(mouseX - lastClickX, mouseY - lastClickY);
            doubleClick = now - lastClickAt < 300L && distance < 6.0;
        }
        Layout layout = layout();
        updateInputBounds(layout);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolean nameHit = offlineName.click(mouseX, mouseY, doubleClick);
            boolean tokenHit = minecraftToken.click(mouseX, mouseY, doubleClick);
            if (nameHit || tokenHit) {
                lastClickAt = now;
                lastClickX = mouseX;
                lastClickY = mouseY;
                return true;
            }
        }

        Alt clicked = accountAt(layout, mouseX, mouseY);
        if (clicked != null) {
            selected = clicked;
            setStatus("Selected " + clicked.getUsername(), false);
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                removeSelected();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubleClick) {
                loginSelected();
            }
            lastClickAt = now;
            lastClickX = mouseX;
            lastClickY = mouseY;
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (layout.back.contains(mouseX, mouseY)) {
            onClose();
        } else if (layout.add.contains(mouseX, mouseY) && !loginRunning) {
            addOffline();
        } else if (layout.microsoft.contains(mouseX, mouseY) && !loginRunning) {
            startMicrosoftLogin();
        } else if (layout.tokenLogin.contains(mouseX, mouseY) && !loginRunning) {
            startTokenLogin();
        } else if (layout.login.contains(mouseX, mouseY) && selected != null && !loginRunning) {
            loginSelected();
        } else if (layout.remove.contains(mouseX, mouseY) && selected != null && !loginRunning) {
            removeSelected();
        }
        lastClickAt = now;
        lastClickX = mouseX;
        lastClickY = mouseY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout layout = layout();
        if (!layout.grid.contains(mouseX, mouseY)) {
            return false;
        }
        int step = delta > 0 ? -layout.columns : delta < 0 ? layout.columns : 0;
        firstVisible += step;
        clampFirstVisible(layout);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & 2) != 0;
        boolean isTab = keyCode == GLFW.GLFW_KEY_TAB;
        boolean isEnter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (isTab) {
            if (offlineName.isFocused()) {
                offlineName.blur();
                minecraftToken.focus();
            } else {
                minecraftToken.blur();
                offlineName.focus();
            }
            return true;
        }
        if (offlineName.isFocused() && isEnter) {
            addOffline();
            return true;
        }
        if (minecraftToken.isFocused() && isEnter) {
            startTokenLogin();
            return true;
        }
        if (offlineName.keyPressed(keyCode, modifiers, Minecraft.getInstance())
                || minecraftToken.keyPressed(keyCode, modifiers, Minecraft.getInstance())) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return control ? false : super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (offlineName.charTyped(codePoint, modifiers)
                || minecraftToken.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void addOffline() {
        if (loginRunning) {
            return;
        }
        String name = offlineName.text().trim();
        if (!AltManager.INSTANCE.addOfflineAlt(name)) {
            setStatus("Invalid or duplicate offline username", true);
            return;
        }
        offlineName.clear();
        refreshAccounts();
        selected = accounts.stream()
                .filter(alt -> alt.getUsername().equalsIgnoreCase(name))
                .findFirst().orElse(selected);
        setStatus("Added " + name, false);
    }

    private void startMicrosoftLogin() {
        loginRunning = true;
        setStatus("Requesting a Microsoft device code...", false);
        AltManager.INSTANCE.startMicrosoftDeviceLogin(new LoginCallback("Microsoft login"), false);
    }

    private void startTokenLogin() {
        if (loginRunning) {
            return;
        }
        String token = minecraftToken.text().trim();
        if (token.isEmpty()) {
            setStatus("Minecraft access token is empty", true);
            return;
        }
        loginRunning = true;
        setStatus("Checking Minecraft access token...", false);
        AltManager.INSTANCE.loginWithMinecraftToken(token, new LoginCallback("Token login"), false);
    }

    private void loginSelected() {
        Alt alt = selected;
        if (alt == null || loginRunning) {
            return;
        }
        if (!alt.isMicrosoft()) {
            AltManager.INSTANCE.login(alt);
            setStatus("Logged in as " + alt.getUsername(), false);
            return;
        }
        loginRunning = true;
        setStatus("Refreshing " + alt.getUsername() + "...", false);
        AltManager.INSTANCE.loginWithRefresh(alt, new LoginCallback("Logged in"), false);
    }

    private void removeSelected() {
        Alt removed = selected;
        if (removed == null || loginRunning) {
            return;
        }
        AltManager.INSTANCE.removeAlt(removed);
        selected = null;
        refreshAccounts();
        setStatus("Removed " + removed.getUsername(), false);
    }

    private void refreshAccounts() {
        String selectedName = selected == null ? null : selected.getUsername();
        accounts = AltManager.INSTANCE.getAlts();
        selected = selectedName == null
                ? AltManager.INSTANCE.getLastAlt().orElse(accounts.isEmpty() ? null : accounts.get(0))
                : accounts.stream()
                .filter(alt -> alt.getUsername().equalsIgnoreCase(selectedName))
                .findFirst()
                .orElse(accounts.isEmpty() ? null : accounts.get(0));
    }

    private Alt accountAt(Layout layout, double mouseX, double mouseY) {
        if (!layout.grid.contains(mouseX, mouseY)) {
            return null;
        }
        int visible = visibleCards(layout);
        for (int slot = 0; slot < visible && firstVisible + slot < accounts.size(); slot++) {
            if (cardBox(layout, slot).contains(mouseX, mouseY)) {
                return accounts.get(firstVisible + slot);
            }
        }
        return null;
    }

    private int visibleRows(Layout layout) {
        return Math.max(1, (int) ((layout.grid.height() + CARD_GAP) / (CARD_HEIGHT + CARD_GAP)));
    }

    private int visibleCards(Layout layout) {
        return visibleRows(layout) * layout.columns;
    }

    private UiControls.Box cardBox(Layout layout, int slot) {
        int row = slot / layout.columns;
        int column = slot % layout.columns;
        float cardWidth = (layout.grid.width() - CARD_GAP * (layout.columns + 1)) / layout.columns;
        float cardX = layout.grid.x() + CARD_GAP + column * (cardWidth + CARD_GAP);
        float cardY = layout.grid.y() + CARD_GAP + row * (CARD_HEIGHT + CARD_GAP);
        return new UiControls.Box(cardX, cardY, cardWidth, CARD_HEIGHT);
    }

    private void clampFirstVisible(Layout layout) {
        int rows = Math.max(1, (int) Math.ceil(accounts.size() / (double) layout.columns));
        int maxRows = Math.max(0, rows - visibleRows(layout));
        int firstRow = Math.max(0, Math.min(firstVisible / layout.columns, maxRows));
        firstVisible = firstRow * layout.columns;
    }

    private void setStatus(String status, boolean error) {
        this.status = status == null ? "" : status;
        this.statusError = error;
    }

    private void updateInputBounds(Layout layout) {
        offlineName.setBounds(layout.offlineName);
        minecraftToken.setBounds(layout.minecraftToken);
    }

    private Layout layout() {
        float workspaceWidth = Math.min(720.0F, Math.max(320.0F, width - 24.0F));
        float workspaceHeight = Math.min(500.0F, Math.max(280.0F, height - 24.0F));
        workspaceWidth = Math.min(workspaceWidth, Math.max(0.0F, width - 8.0F));
        workspaceHeight = Math.min(workspaceHeight, Math.max(0.0F, height - 8.0F));
        float x = Math.max(4.0F, (width - workspaceWidth) * 0.5F);
        float y = Math.max(4.0F, (height - workspaceHeight) * 0.5F);
        UiControls.Box workspace = new UiControls.Box(x, y, workspaceWidth, workspaceHeight);
        UiControls.Box topBar = new UiControls.Box(x, y, workspaceWidth, 34.0F);
        UiControls.Box statusBar = new UiControls.Box(x, y + workspaceHeight - 21.0F,
                workspaceWidth, 21.0F);
        UiControls.Box back = new UiControls.Box(x + workspaceWidth - 64.0F, y + 6.0F, 54.0F, 22.0F);

        float padding = 14.0F;
        float contentX = x + padding;
        float contentWidth = workspaceWidth - padding * 2.0F;
        int columns = workspaceWidth >= 560 ? 3 : workspaceWidth >= 400 ? 2 : 1;

        float rowHeight = 24.0F;
        float rowGap = 8.0F;
        float labelGap = 15.0F;
        float actionBarHeight = labelGap + rowHeight
                + rowGap + labelGap + rowHeight
                + rowGap + rowHeight
                + rowGap + rowHeight;
        float actionBarTop = statusBar.y() - 8.0F - actionBarHeight;

        float gridTop = y + 42.0F;
        UiControls.Box grid = new UiControls.Box(x, gridTop, workspaceWidth,
                Math.max(CARD_HEIGHT + CARD_GAP * 2, actionBarTop - gridTop - 8.0F));

        float buttonWidth = Math.min(70.0F, Math.max(44.0F, contentWidth * 0.16F));
        float fieldWidth = contentWidth - buttonWidth - 8.0F;

        float nameY = actionBarTop + labelGap;
        UiControls.Box name = new UiControls.Box(contentX, nameY, fieldWidth, rowHeight);
        UiControls.Box add = new UiControls.Box(contentX + fieldWidth + 8.0F, nameY,
                buttonWidth, rowHeight);

        float tokenY = nameY + rowHeight + rowGap + labelGap;
        UiControls.Box token = new UiControls.Box(contentX, tokenY, fieldWidth, rowHeight);
        UiControls.Box tokenLogin = new UiControls.Box(contentX + fieldWidth + 8.0F, tokenY,
                buttonWidth, rowHeight);

        float actionsY = tokenY + rowHeight + rowGap;
        UiControls.Box microsoft = new UiControls.Box(contentX, actionsY, contentWidth, rowHeight);

        float useRemoveY = actionsY + rowHeight + rowGap;
        float half = (contentWidth - 8.0F) * 0.5F;
        UiControls.Box login = new UiControls.Box(contentX, useRemoveY, half, rowHeight);
        UiControls.Box remove = new UiControls.Box(contentX + half + 8.0F, useRemoveY, half, rowHeight);

        return new Layout(workspace, topBar, statusBar, back, grid, name, add, token, tokenLogin,
                microsoft, login, remove, columns);
    }

    private static boolean sameAccount(Alt first, Alt second) {
        return first != null && second != null
                && first.getUsername().equalsIgnoreCase(second.getUsername());
    }

    private static String firstLetter(String name) {
        return name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
    }

    private static String accountType(Alt alt) {
        if (!alt.isMicrosoft()) {
            return "Offline account";
        }
        if (alt.isExpired()) {
            return alt.canRefresh() ? "Microsoft - refresh required" : "Minecraft token expired";
        }
        long hours = Duration.ofSeconds(alt.getLeftExpiringTime()).toHours();
        return (alt.canRefresh() ? "Microsoft" : "Minecraft token") + " - " + hours + "h left";
    }

    private record Layout(
            UiControls.Box workspace,
            UiControls.Box topBar,
            UiControls.Box statusBar,
            UiControls.Box back,
            UiControls.Box grid,
            UiControls.Box offlineName,
            UiControls.Box add,
            UiControls.Box minecraftToken,
            UiControls.Box tokenLogin,
            UiControls.Box microsoft,
            UiControls.Box login,
            UiControls.Box remove,
            int columns
    ) {
    }

    private final class LoginCallback implements MicrosoftAuthService.LoginCallback {

        private final String successPrefix;

        private LoginCallback(String successPrefix) {
            this.successPrefix = successPrefix;
        }

        @Override
        public void setStatus(String message) {
            Minecraft.getInstance().execute(() -> AltManagerScreen.this.setStatus(message, false));
        }

        @Override
        public void onSucceed(Alt alt) {
            Minecraft.getInstance().execute(() -> {
                AltManager.INSTANCE.login(alt);
                loginRunning = false;
                refreshAccounts();
                selected = accounts.stream()
                        .filter(account -> account.getUsername().equalsIgnoreCase(alt.getUsername()))
                        .findFirst()
                        .orElse(alt);
                AltManagerScreen.this.setStatus(successPrefix + " as " + alt.getUsername(), false);
            });
        }

        @Override
        public void onFailed(Exception error) {
            Minecraft.getInstance().execute(() -> {
                loginRunning = false;
                String message = error == null ? "Unknown error" : error.getMessage();
                AltManagerScreen.this.setStatus("Microsoft login failed: " + message, true);
            });
        }
    }
}
