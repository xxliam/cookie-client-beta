package info.u_team.oauth_account_manager.screen;

import info.u_team.oauth_account_manager.init.OAuthAccountManagerLocalization;
import info.u_team.oauth_account_manager.screen.list.AbstractAccountSelectionEntry;
import info.u_team.oauth_account_manager.screen.list.AccountSelectionEntry;
import info.u_team.oauth_account_manager.screen.list.AccountSelectionList;
import info.u_team.u_team_core.gui.elements.UButton;
import info.u_team.u_team_core.screen.UScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AccountsScreen extends UScreen {
	
	private final Screen lastScreen;
	
	private final AccountSelectionList list;
	
	private UButton useButton;
	private UButton deleteButton;
	
	public AccountsScreen(Screen lastScreen) {
		super(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACCOUNTS_TITLE));
		this.lastScreen = lastScreen;
		list = new AccountSelectionList(this) {
			
			@Override
			public void setSelected(AbstractAccountSelectionEntry selected) {
				super.setSelected(selected);
				updateButtonState(selected);
			}
		};
	}
	
	@Override
	protected void init() {
		super.init();
		
		final UButton button = addRenderableWidget(MultiplayerScreenAdditions.addButton(width, lastScreen));
		button.setPressable(() -> {
			resize(minecraft, width, height);
		});
		
		addRenderableWidget(list);
		list.updateSettings(width, height, 32, height - 64, 0, width);
		
		useButton = addRenderableWidget(new UButton(0, 0, 74, 20, Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACCOUNTS_USE_BUTTON)));
		useButton.setPressable(list::useSelectedEntry);
		
		final UButton addButton = addRenderableWidget(new UButton(0, 0, 74, 20, Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACCOUNTS_ADD_BUTTON)));
		addButton.setPressable(() -> {
			minecraft.setScreen(new AddAccountOpenLinkScreen(this));
		});
		
		deleteButton = addRenderableWidget(new UButton(0, 0, 74, 20, Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACCOUNTS_DELETE_BUTTON)));
		deleteButton.setPressable(() -> {
			if (list.getSelected() instanceof AccountSelectionEntry entry) {
				minecraft.setScreen(new AccountDeleteScreen(this, entry.getProfile(), list::deleteSelectedEntry));
			}
		});
		
		final UButton cancelButton = addRenderableWidget(new UButton(0, 0, 74, 20, CommonComponents.GUI_CANCEL));
		cancelButton.setPressable(() -> minecraft.setScreen(lastScreen));
		
		final LinearLayout layout = new LinearLayout(308, 20, LinearLayout.Orientation.HORIZONTAL);
		layout.addChild(useButton);
		layout.addChild(addButton);
		layout.addChild(deleteButton);
		layout.addChild(cancelButton);
		layout.arrangeElements();
		
		FrameLayout.centerInRectangle(layout, 0, height - 64, width, 64);
		
		updateButtonState(null);
		
		list.loadEntries();
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
	
	public void updateButtonState(AbstractAccountSelectionEntry entry) {
		if (entry == null) {
			useButton.active = false;
			deleteButton.active = false;
		} else {
			useButton.active = true;
			deleteButton.active = entry instanceof AccountSelectionEntry;
		}
	}
	
}
