package info.u_team.oauth_account_manager.screen;

import java.util.Optional;

import info.u_team.oauth_account_manager.OAuthAccountManagerReference;
import info.u_team.oauth_account_manager.init.OAuthAccountManagerLocalization;
import net.hycrafthd.minecraft_authenticator.login.AuthenticationException;
import net.hycrafthd.simple_minecraft_authenticator.method.AuthenticationMethod;
import net.hycrafthd.simple_minecraft_authenticator.result.AuthenticationResult;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AddAccountOAuthScreen extends CommonWaitingScreen {
	
	public AddAccountOAuthScreen(Screen lastScreen) {
		super(lastScreen, lastScreen, Component.translatable(OAuthAccountManagerLocalization.SCREEN_ADD_ACCOUNT_INFORMATION_TITLE));
	}
	
	@Override
	protected void init() {
		super.init();
		setInformationMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ADD_ACCOUNT_INFORMATION_MESSAGE_WAITING_FOR_LOGIN));
		spinnerWidget.setTooltip(Tooltip.create(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ADD_ACCOUNT_INFORMATION_SPINNER_TOOLTIP_WAITING)));
	}
	
	public void authenticate(AuthenticationMethod method) {
		createWaitingThread(() -> {
			final AuthenticationResult result;
			try {
				result = method.initalAuthentication();
			} catch (final AuthenticationException ex) {
				if (!(ex.getCause() instanceof InterruptedException)) {
					minecraft.execute(() -> setFinalMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ADD_ACCOUNT_INFORMATION_MESSAGE_ERROR, ex.getLocalizedMessage())));
					OAuthAccountManagerReference.LOGGER.warn("Microsoft OAuth didn't complete sucessfully", ex);
				}
				return;
			}
			
			minecraft.execute(() -> {
				final AccountLoginScreen screen = new AccountLoginScreen(lastScreen);
				screen.login(Optional.empty(), () -> result, null);
				minecraft.setScreen(screen);
			});
		});
	}
}
