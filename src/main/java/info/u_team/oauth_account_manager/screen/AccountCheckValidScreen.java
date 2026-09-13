package info.u_team.oauth_account_manager.screen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import info.u_team.oauth_account_manager.init.OAuthAccountManagerLocalization;
import info.u_team.oauth_account_manager.util.AuthenticationUtil;
import info.u_team.u_team_core.gui.elements.UButton;
import info.u_team.u_team_core.screen.UScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AccountCheckValidScreen extends UScreen {
	
	private final Screen lastScreen;
	private final Runnable retryCallback;
	private final Runnable validCallback;
	
	private CompletableFuture<?> future;
	
	private MultiLineTextWidget messageWidget;
	private UButton retryButton;
	private UButton cancelButton;
	
	public AccountCheckValidScreen(Screen lastScreen, Runnable retryCallback, Runnable validCallback) {
		super(Component.translatable(OAuthAccountManagerLocalization.SCREEN_CHECK_ACCOUNT_VALID_TITLE));
		this.lastScreen = lastScreen;
		this.retryCallback = retryCallback;
		this.validCallback = validCallback;
	}
	
	@Override
	protected void init() {
		super.init();
		
		messageWidget = addRenderableWidget(new MultiLineTextWidget(0, (height / 2) - 20, CommonComponents.EMPTY, font).setMaxWidth(width - 50).setCentered(true));
		setInformationMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_CHECK_ACCOUNT_VALID_MESSAGE_CHECKING));
		
		retryButton = addRenderableWidget(new UButton(0, 0, 100, 20, Component.translatable(OAuthAccountManagerLocalization.SCREEN_CHECK_ACCOUNT_VALID_BUTTON_RETRY)));
		retryButton.setPressable(retryCallback::run);
		retryButton.active = false;
		
		cancelButton = addRenderableWidget(new UButton(0, 0, 100, 20, CommonComponents.GUI_CANCEL));
		cancelButton.setPressable(this::cancelChecking);
		
		final LinearLayout layout = new LinearLayout(205, 20, LinearLayout.Orientation.HORIZONTAL);
		layout.addChild(retryButton);
		layout.addChild(cancelButton);
		layout.arrangeElements();
		
		FrameLayout.centerInRectangle(layout, 0, height - 64, width, 64);
	}
	
	@Override
	public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.renderForeground(guiGraphics, mouseX, mouseY, partialTick);
		
		guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
	}
	
	@Override
	public void onClose() {
		cancelChecking();
	}
	
	@Override
	protected void repositionElements() {
		final Component messageWidgetMessage = messageWidget.getMessage();
		final boolean retryButtonActive = retryButton.active;
		super.repositionElements();
		setInformationMessage(messageWidgetMessage);
		retryButton.active = retryButtonActive;
	}
	
	protected void setInformationMessage(Component component) {
		messageWidget.setMessage(component);
		messageWidget.setX((width / 2) - (messageWidget.getWidth() / 2));
	}
	
	protected void cancelChecking() {
		if (future != null) {
			future.cancel(false);
		}
		minecraft.setScreen(lastScreen);
	}
	
	public void checkAccount(String accessToken) {
		future = AuthenticationUtil.isAccessTokenValid(accessToken).orTimeout(30, TimeUnit.SECONDS).thenAccept(valid -> {
			minecraft.execute(() -> {
				if (valid) {
					validCallback.run();
				} else {
					retryButton.active = true;
					setInformationMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_CHECK_ACCOUNT_VALID_MESSAGE_INVALID));
				}
			});
		});
	}
}
