package info.u_team.oauth_account_manager.screen.widget;

import java.util.List;
import java.util.Optional;

import com.mojang.authlib.GameProfile;

import info.u_team.u_team_core.api.gui.PerspectiveRenderable;
import info.u_team.u_team_core.api.gui.TooltipRenderable;
import info.u_team.u_team_core.util.WidgetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PlayerIconWidget extends AbstractWidget implements PerspectiveRenderable, TooltipRenderable {
	
	private final Minecraft minecraft = Minecraft.getInstance();
	
	private GameProfile profile;
	
	public PlayerIconWidget(int x, int y, int size, GameProfile profile) {
		super(x, y, size, size, CommonComponents.EMPTY);
		this.profile = profile;
	}
	
	public void setProfile(GameProfile profile) {
		this.profile = profile;
	}
	
	public GameProfile getProfile() {
		return profile;
	}
	
	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (profile != null) {
			renderBackground(guiGraphics, mouseX, mouseY, partialTick);
			renderForeground(guiGraphics, mouseX, mouseY, partialTick);
			WidgetUtil.renderCustomTooltipForWidget(this, guiGraphics, mouseX, mouseY, partialTick);
		}
	}
	
	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		final ResourceLocation skin = minecraft.getSkinManager().getInsecureSkinLocation(profile);
		
		PlayerFaceRenderer.draw(guiGraphics, skin, getX(), getY(), getWidth(), false, false);
	}
	
	@Override
	public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
	}
	
	@Override
	public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (isHovered) {
			guiGraphics.renderTooltip(minecraft.font, List.of(Component.literal(profile.getName())), Optional.empty(), mouseX, mouseY);
		}
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput element) {
		defaultButtonNarrationText(element);
	}
	
	@Override
	public void playDownSound(SoundManager pHandler) {
		// Don't play click sound
	}
	
}
