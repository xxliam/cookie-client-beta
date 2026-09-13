package info.u_team.oauth_account_manager.screen;

import com.mojang.authlib.GameProfile;

import info.u_team.oauth_account_manager.init.OAuthAccountManagerLocalization;
import info.u_team.oauth_account_manager.screen.widget.PlayerIconWidget;
import info.u_team.u_team_core.gui.elements.UButton;
import info.u_team.u_team_core.screen.UScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AccountDeleteScreen extends UScreen {
	
	private final Screen lastScreen;
	
	private final GameProfile gameProfile;
	private final Runnable deleteCallback;
	
	private MultiLineTextWidget messageWidget;
	private PlayerIconWidget playerIconWidget;
	
	public AccountDeleteScreen(Screen lastScreen, GameProfile gameProfile, Runnable deleteCallback) {
		super(Component.translatable(OAuthAccountManagerLocalization.SCREEN_DELETE_ACCOUNT_TITLE));
		this.lastScreen = lastScreen;
		this.gameProfile = gameProfile;
		this.deleteCallback = deleteCallback;
	}
	
	@Override
	protected void init() {
		super.init();
		
		messageWidget = addRenderableWidget(new MultiLineTextWidget(0, (height / 2) - 60, CommonComponents.EMPTY, font).setMaxWidth(width - 50).setCentered(true));
		setInformationMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_DELETE_ACCOUNT_MESSAGE, gameProfile.getName()));
		
		playerIconWidget = addRenderableWidget(new PlayerIconWidget(width / 2 - 32, height / 2 - 32, 64, gameProfile));
		
		final UButton deleteButton = addRenderableWidget(new UButton(0, 0, 100, 20, Component.translatable(OAuthAccountManagerLocalization.SCREEN_DELETE_ACCOUNT_DELETE_BUTTON)));
		deleteButton.setPressable(() -> {
			deleteCallback.run();
			minecraft.setScreen(lastScreen);
		});
		
		final UButton cancelButton = addRenderableWidget(new UButton(0, 0, 100, 20, CommonComponents.GUI_CANCEL));
		cancelButton.setPressable(() -> minecraft.setScreen(lastScreen));
		
		final LinearLayout layout = new LinearLayout(205, 20, LinearLayout.Orientation.HORIZONTAL);
		layout.addChild(deleteButton);
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
		minecraft.setScreen(lastScreen);
	}
	
	@Override
	protected void repositionElements() {
		final Component messageWidgetMessage = messageWidget.getMessage();
		final GameProfile profile = playerIconWidget.getProfile();
		super.repositionElements();
		setInformationMessage(messageWidgetMessage);
		playerIconWidget.setProfile(profile);
	}
	
	protected void setInformationMessage(Component component) {
		messageWidget.setMessage(component);
		messageWidget.setX((width / 2) - (messageWidget.getWidth() / 2));
	}
}
