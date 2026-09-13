package info.u_team.oauth_account_manager.init;

import info.u_team.oauth_account_manager.OAuthAccountManagerMod;
import info.u_team.u_team_core.api.construct.Construct;
import info.u_team.u_team_core.api.construct.ModConstruct;

@Construct(modid = OAuthAccountManagerMod.MODID, client = true)
public class OAuthAccountManagerClientConstruct implements ModConstruct {
	
	@Override
	public void construct() {
		OAuthAccountManagerEventHandler.register();
	}
}