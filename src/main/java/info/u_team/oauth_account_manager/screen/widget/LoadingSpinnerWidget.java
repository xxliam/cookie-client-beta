package info.u_team.oauth_account_manager.screen.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import info.u_team.oauth_account_manager.OAuthAccountManagerReference;
import info.u_team.u_team_core.api.gui.PerspectiveRenderable;
import info.u_team.u_team_core.util.RGBA;
import info.u_team.u_team_core.util.RenderUtil;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;

public class LoadingSpinnerWidget extends AbstractWidget implements PerspectiveRenderable {
	
	private static final ResourceLocation SPINNER = new ResourceLocation(OAuthAccountManagerReference.MODID, "textures/gui/spinner.png");
	
	private long lastTime;
	private int currentRotation;
	
	public LoadingSpinnerWidget(int x, int y, int width, int height) {
		super(x, y, width, height, CommonComponents.EMPTY);
	}
	
	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		renderForeground(guiGraphics, mouseX, mouseY, partialTick);
	}
	
	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		final PoseStack poseStack = guiGraphics.pose();
		
		poseStack.pushPose();
		
		poseStack.translate(getX(), getY(), 0);
		
		final float xRot = width / 2f;
		final float yRot = height / 2f;
		
		poseStack.translate(xRot, yRot, 0);
		poseStack.mulPose(Axis.ZP.rotationDegrees(currentRotation));
		poseStack.translate(-xRot, -yRot, 0);
		
		RenderUtil.drawTexturedQuad(poseStack, 0, 0 + width, 0, 0 + height, 0, 1, 0, 1, 0, SPINNER, RGBA.WHITE);
		
		poseStack.popPose();
		
		if (Util.getMillis() - lastTime > 10) {
			lastTime = Util.getMillis();
			currentRotation += 2;
		}
	}
	
	@Override
	public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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
