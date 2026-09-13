package info.u_team.oauth_account_manager;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import info.u_team.oauth_account_manager.init.OAuthAccountManagerEventHandler;
import info.u_team.oauth_account_manager.util.MinecraftAccounts;

/**
 * Ported into cookie-client (merged codebase): there is no dedicated mod container for the
 * id "oauthaccountmanager", so u_team_core's annotation scanner
 * ({@code AnnotationManager.callAnnotations}) would throw
 * {@code IllegalStateException: Mod oauthaccountmanager is not loaded}.
 * The two {@code @Construct} bodies are therefore invoked directly here.
 */
public class OAuthAccountManagerMod implements ClientModInitializer {

	public static final String MODID = OAuthAccountManagerReference.MODID;
	public static final Logger LOGGER = OAuthAccountManagerReference.LOGGER;

	@Override
	public void onInitializeClient() {
		// Body of OAuthAccountManagerCommonClientConstruct.construct()
		if (Minecraft.getInstance() != null) {
			MinecraftAccounts.enqueueLoad();
		}

		// Body of OAuthAccountManagerClientConstruct.construct()
		OAuthAccountManagerEventHandler.register();
	}

}
