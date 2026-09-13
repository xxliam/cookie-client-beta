package info.u_team.oauth_account_manager.init;

import info.u_team.oauth_account_manager.screen.MultiplayerScreenAdditions;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

public class OAuthAccountManagerEventHandler {
	
	private static void onScreenInitPost(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
		if (!(screen instanceof JoinMultiplayerScreen multiplayerScreen)) {
			return;
		}
		Screens.getButtons(screen).add(MultiplayerScreenAdditions.addButton(multiplayerScreen.width, multiplayerScreen));
	}
	
	public static void register() {
		ScreenEvents.AFTER_INIT.register(OAuthAccountManagerEventHandler::onScreenInitPost);
	}
	
}
