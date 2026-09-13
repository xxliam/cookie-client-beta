package info.u_team.oauth_account_manager.screen.list;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import info.u_team.oauth_account_manager.screen.AccountCheckValidScreen;
import info.u_team.oauth_account_manager.screen.AccountLoginScreen;
import info.u_team.oauth_account_manager.screen.AccountUseScreen;
import info.u_team.oauth_account_manager.util.AuthenticationUtil;
import info.u_team.oauth_account_manager.util.LoadedAccount;
import info.u_team.oauth_account_manager.util.MinecraftAccounts;
import net.minecraft.client.gui.screens.Screen;

public class AccountSelectionEntry extends AbstractAccountSelectionEntry {
	
	public AccountSelectionEntry(Screen ourScreen, AccountSelectionList selectionList, UUID uuid, GameProfile profile) {
		super(ourScreen, selectionList, uuid, profile);
	}
	
	@Override
	protected void useEntry() {
		final UUID uuid = getUuid();
		
		if (!MinecraftAccounts.isLoaded(uuid)) {
			showLoginScreen(uuid);
		} else {
			showUseScreen(uuid, () -> showLoginScreen(uuid));
		}
	}
	
	private void showLoginScreen(UUID uuid) {
		final AccountLoginScreen loginScreen = new AccountLoginScreen(ourScreen);
		loginScreen.login(Optional.of(uuid), () -> AuthenticationUtil.createWebAuthenticationMethod().existingAuthentication(MinecraftAccounts.getAccount(uuid)), screen -> {
			showUseScreen(uuid, () -> showLoginScreen(uuid));
		});
		minecraft.setScreen(loginScreen);
	}
	
	private void showUseScreen(UUID uuid, Runnable retry) {
		final LoadedAccount loadedAccount = MinecraftAccounts.getLoadedAccount(uuid);
		if (loadedAccount == null) {
			retry.run();
			return;
		}
		final AccountCheckValidScreen validScreen = new AccountCheckValidScreen(ourScreen, retry, () -> {
			final AccountUseScreen useScreen = new AccountUseScreen(ourScreen, getProfile(), () -> AuthenticationUtil.createMinecraftAccountData(loadedAccount, getProfile()));
			minecraft.setScreen(useScreen);
		});
		validScreen.checkAccount(loadedAccount.user().accessToken());
		minecraft.setScreen(validScreen);
	}
	
}
