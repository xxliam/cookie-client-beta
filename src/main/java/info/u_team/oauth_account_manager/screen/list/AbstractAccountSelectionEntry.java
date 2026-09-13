package info.u_team.oauth_account_manager.screen.list;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;

import info.u_team.oauth_account_manager.init.OAuthAccountManagerLocalization;
import info.u_team.u_team_core.gui.elements.ScrollableListEntry;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

public abstract class AbstractAccountSelectionEntry extends ScrollableListEntry<AbstractAccountSelectionEntry> {
	
	protected final Screen ourScreen;
	private final AccountSelectionList selectionList;
	
	private final UUID uuid;
	private final GameProfile profile;
	
	protected final boolean currentlyUsed;
	
	private long lastClickTime;
	
	public AbstractAccountSelectionEntry(Screen ourScreen, AccountSelectionList selectionList, UUID uuid, GameProfile profile) {
		this.ourScreen = ourScreen;
		this.selectionList = selectionList;
		this.uuid = uuid;
		this.profile = profile;
		this.currentlyUsed = uuid.equals(minecraft.getUser().getProfileId());
	}
	
	@Override
	public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
		final ResourceLocation skin = minecraft.getSkinManager().getInsecureSkinLocation(profile);
		
		PlayerFaceRenderer.draw(guiGraphics, skin, left, top, 32, false, false);
		
		guiGraphics.drawString(minecraft.font, getTitleName(), left + 35, top + 1, 0xFFFFFF, false);
		guiGraphics.drawString(minecraft.font, uuid.toString(), left + 35, top + 12, 0x808080, false);
		
		if (hovered && currentlyUsed) {
			ourScreen.setTooltipForNextRenderPass(minecraft.font.split(Component.translatable(OAuthAccountManagerLocalization.SCREEN_ACCOUNTS_LIST_USED_TOOLTIP), 175));
		}
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			playClickSound();
			selectionList.useSelectedEntry();
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (Util.getMillis() - lastClickTime < 250) {
			playClickSound();
			selectionList.useSelectedEntry();
			return true;
		}
		lastClickTime = Util.getMillis();
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	private void playClickSound() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}
	
	@Override
	public Component getNarration() {
		return Component.literal(profile.getName());
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public GameProfile getProfile() {
		return profile;
	}
	
	protected abstract void useEntry();
	
	protected MutableComponent getTitleName() {
		return Component.literal(profile.getName()).withStyle(style -> style.withColor(currentlyUsed ? 0x55FF55 : 0xFFFFFF).withItalic(false));
	}
	
}
