package info.u_team.oauth_account_manager.screen;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import com.mojang.util.UUIDTypeAdapter;

import info.u_team.oauth_account_manager.OAuthAccountManagerReference;
import info.u_team.oauth_account_manager.init.OAuthAccountManagerLocalization;
import info.u_team.oauth_account_manager.screen.widget.PlayerIconWidget;
import info.u_team.oauth_account_manager.util.LoadedAccount;
import info.u_team.oauth_account_manager.util.MinecraftAccounts;
import net.hycrafthd.minecraft_authenticator.login.AuthenticationException;
import net.hycrafthd.minecraft_authenticator.login.Authenticator;
import net.hycrafthd.minecraft_authenticator.login.LoginState;
import net.hycrafthd.minecraft_authenticator.login.User;
import net.hycrafthd.minecraft_authenticator.login.XBoxProfile;
import net.hycrafthd.simple_minecraft_authenticator.SimpleMinecraftAuthentication;
import net.hycrafthd.simple_minecraft_authenticator.result.AuthenticationResult;
import net.hycrafthd.simple_minecraft_authenticator.util.AuthenticationFutureUtil;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AccountLoginScreen extends CommonWaitingScreen {
	
	private PlayerIconWidget playerIconWidget;
	
	public AccountLoginScreen(Screen lastScreen) {
		super(lastScreen, lastScreen, Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_TITLE));
	}
	
	@Override
	protected void init() {
		super.init();
		setInformationMessage(getLoginStateComponent(LoginState.INITAL_FILE));
		spinnerWidget.setTooltip(Tooltip.create(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_SPINNER_TOOLTIP_LOGGING)));
		
		playerIconWidget = addRenderableWidget(new PlayerIconWidget(width / 2 - 32, height / 2 - 32, 64, null));
	}
	
	@Override
	protected void repositionElements() {
		final GameProfile profile = playerIconWidget.getProfile();
		super.repositionElements();
		playerIconWidget.setProfile(profile);
	}
	
	private Component getLoginStateComponent(LoginState state) {
		return Component.translatable(switch (state) {
		case INITAL_FILE -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_INITAL_FILE;
		case LOGIN_MICOSOFT -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_LOGIN_MICOSOFT;
		case XBL_TOKEN -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_XBL_TOKEN;
		case XSTS_TOKEN -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_XSTS_TOKEN;
		case ACCESS_TOKEN -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_ACCESS_TOKEN;
		case ENTITLEMENT -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_ENTITLEMENT;
		case PROFILE -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_PROFILE;
		case XBOX_PROFILE -> OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_LOGIN_STATE_INFO_XBOX_PROFILE;
		});
	}
	
	public void login(Optional<UUID> accountUUID, Supplier<AuthenticationResult> authenticationResult, Consumer<AccountLoginScreen> callback) {
		createWaitingThread(() -> {
			final Authenticator authenticator = authenticationResult.get().buildAuthenticator(true);
			try {
				AuthenticationFutureUtil.runAuthentication(SimpleMinecraftAuthentication.getExecutor(), () -> {
					// Run authentication to minecraft services
					authenticator.run(state -> {
						minecraft.execute(() -> setInformationMessage(getLoginStateComponent(state)));
					});
					return null;
				}, 300, true);
			} catch (final AuthenticationException ex) {
				if (!(ex.getCause() instanceof InterruptedException)) {
					minecraft.execute(() -> setFinalMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_INFORMATION_MESSAGE_ERROR, ex.getLocalizedMessage())));
					OAuthAccountManagerReference.LOGGER.warn("Authentication with minecraft services didn't complete sucessfully", ex);
				}
				
				// In error state update authentication file if uuid is known already
				accountUUID.ifPresent(uuid -> {
					if (authenticator.getResultFile() != null) {
						MinecraftAccounts.updateAuthenticationFile(uuid, authenticator.getResultFile());
					}
				});
				return;
			}
			
			final User user = authenticator.getUser().orElseThrow(AssertionError::new);
			final XBoxProfile xboxProfile = authenticator.getXBoxProfile().orElseThrow(AssertionError::new);
			
			// Parse uuid and validate
			final UUID uuid;
			try {
				uuid = UUIDTypeAdapter.fromString(user.uuid());
				
				if (accountUUID.isPresent()) {
					if (!accountUUID.get().equals(uuid)) {
						throw new IllegalArgumentException("UUID returned from minecraft services did not match existing uuid for that account");
					}
				}
			} catch (final IllegalArgumentException ex) {
				minecraft.execute(() -> setFinalMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_INFORMATION_MESSAGE_ERROR, ex.getLocalizedMessage())));
				OAuthAccountManagerReference.LOGGER.error("Cannot add minecraft account", ex);
				return;
			}
			
			// Retrieve game profile
			final GameProfile profile = minecraft.getMinecraftSessionService().fillProfileProperties(new GameProfile(uuid, null), false);
			
			// Add account
			MinecraftAccounts.addAccount(uuid, authenticator.getResultFile(), profile, new LoadedAccount(user, xboxProfile));
			
			minecraft.execute(() -> {
				playerIconWidget.setProfile(profile);
				setFinalMessage(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACOUNT_LOGIN_INFORMATION_MESSAGE_SUCCESS, authenticator.getUser().get().name()));
				if (callback != null) {
					callback.accept(this);
				}
			});
		});
	}
}
