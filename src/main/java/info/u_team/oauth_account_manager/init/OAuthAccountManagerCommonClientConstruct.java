package info.u_team.oauth_account_manager.init;

import info.u_team.oauth_account_manager.OAuthAccountManagerReference;
import info.u_team.oauth_account_manager.util.MinecraftAccounts;
import info.u_team.u_team_core.api.construct.Construct;
import info.u_team.u_team_core.api.construct.ModConstruct;
import net.minecraft.client.Minecraft;

@Construct(modid = OAuthAccountManagerReference.MODID, client = true)
public class OAuthAccountManagerCommonClientConstruct implements ModConstruct {
	
	@Override
	public void construct() {
		if (Minecraft.getInstance() != null) {
			MinecraftAccounts.enqueueLoad();
		}
	}
}