package info.u_team.oauth_account_manager.screen;

import info.u_team.oauth_account_manager.init.OAuthAccountManagerLocalization;
import info.u_team.oauth_account_manager.util.AuthenticationUtil;
import info.u_team.u_team_core.gui.elements.UButton;
import info.u_team.u_team_core.util.RGBA;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MultiplayerScreenAdditions {
	
	private static final RGBA VALID_COLOR = RGBA.fromARGB(0xFF2CFC03);
	private static final RGBA INVALID_COLOR = RGBA.fromARGB(0xFFFC0303);
	
	public static UButton addButton(int width, Screen lastScreen) {
		final UButton button = new UButton(0, 0, 74, 16, Component.translatable(OAuthAccountManagerLocalization.SCREEN_MULTIPLAYER_ADDITION_ACCOUNT_BUTTON));
		FrameLayout.centerInRectangle(button, 117, 0, width, 45);
		button.setPressable(() -> {
			Minecraft.getInstance().setScreen(new AccountsScreen(lastScreen));
		});
		button.setTooltip(Tooltip.create(Component.translatable(OAuthAccountManagerLocalization.SCREEN_MULTIPLAYER_ADDITION_ACCOUNT_BUTTON_TOOLTIP_CHECKING)));
		
		final User user = Minecraft.getInstance().getUser();
		AuthenticationUtil.isAccessTokenValid(user.getAccessToken()).thenAccept(isValid -> {
			final RGBA color;
			final Component tooltip;
			if (isValid) {
				color = VALID_COLOR;
				tooltip = Component.translatable(OAuthAccountManagerLocalization.SCREEN_MULTIPLAYER_ADDITION_ACCOUNT_BUTTON_TOOLTIP_VALID, user.getName()).withStyle(ChatFormatting.GREEN);
			} else {
				color = INVALID_COLOR;
				tooltip = Component.translatable(OAuthAccountManagerLocalization.SCREEN_MULTIPLAYER_ADDITION_ACCOUNT_BUTTON_TOOLTIP_INVALID).withStyle(ChatFormatting.RED);
			}
			Minecraft.getInstance().execute(() -> {
				button.setButtonColor(color);
				button.setTooltip(Tooltip.create(tooltip));
			});
		});
		return button;
	}
	
}
