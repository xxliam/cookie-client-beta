package info.u_team.oauth_account_manager.screen;

import info.u_team.oauth_account_manager.screen.widget.LoadingSpinnerWidget;
import info.u_team.u_team_core.gui.elements.UButton;
import info.u_team.u_team_core.screen.UScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class CommonWaitingScreen extends UScreen {
	
	protected final Screen lastScreen;
	protected final Screen doneScreen;
	
	protected MultiLineTextWidget messageWidget;
	protected LoadingSpinnerWidget spinnerWidget;
	protected UButton doneButton;
	protected UButton cancelButton;
	
	protected Thread waitingThread;
	
	public CommonWaitingScreen(Screen lastScreen, Screen doneScreen, Component title) {
		super(title);
		this.lastScreen = lastScreen;
		this.doneScreen = doneScreen;
	}
	
	@Override
	protected void init() {
		super.init();
		
		messageWidget = addRenderableWidget(new MultiLineTextWidget(0, (height / 2) - 60, CommonComponents.EMPTY, font).setMaxWidth(width - 50).setCentered(true));
		
		spinnerWidget = addRenderableWidget(new LoadingSpinnerWidget(0, 0, 60, 60));
		FrameLayout.centerInRectangle(spinnerWidget, 0, 0, width, height);
		
		doneButton = addRenderableWidget(new UButton(0, 0, 100, 20, CommonComponents.GUI_DONE));
		doneButton.setPressable(() -> minecraft.setScreen(doneScreen));
		doneButton.active = false;
		
		cancelButton = addRenderableWidget(new UButton(0, 0, 100, 20, CommonComponents.GUI_CANCEL));
		cancelButton.setPressable(this::cancelWaitingThread);
		
		final LinearLayout layout = new LinearLayout(205, 20, LinearLayout.Orientation.HORIZONTAL);
		layout.addChild(doneButton);
		layout.addChild(cancelButton);
		layout.arrangeElements();
		
		FrameLayout.centerInRectangle(layout, 0, height - 64, width, 64);
	}
	
	@Override
	public void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.renderForeground(guiGraphics, mouseX, mouseY, partialTick);
		
		guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
	}
	
	@Override
	public void onClose() {
		cancelWaitingThread();
	}
	
	@Override
	protected void repositionElements() {
		final Component messageWidgetMessage = messageWidget.getMessage();
		final boolean spinnerWidgetActive = spinnerWidget.active;
		final boolean spinnerWidgetVisible = spinnerWidget.visible;
		final boolean doneButtonActive = doneButton.active;
		final boolean cancelButtonActive = cancelButton.active;
		super.repositionElements();
		setInformationMessage(messageWidgetMessage);
		spinnerWidget.active = spinnerWidgetActive;
		spinnerWidget.visible = spinnerWidgetVisible;
		doneButton.active = doneButtonActive;
		cancelButton.active = cancelButtonActive;
	}
	
	protected void setInformationMessage(Component component) {
		messageWidget.setMessage(component);
		messageWidget.setX((width / 2) - (messageWidget.getWidth() / 2));
	}
	
	protected void setFinalMessage(Component component) {
		setInformationMessage(component);
		doneButton.active = true;
		cancelButton.active = false;
		spinnerWidget.active = false;
		spinnerWidget.visible = false;
	}
	
	protected void cancelWaitingThread() {
		if (waitingThread != null) {
			waitingThread.interrupt();
		}
		minecraft.setScreen(lastScreen);
	}
	
	protected void createWaitingThread(Runnable runnable) {
		waitingThread = new Thread(runnable, "OAuth-Account-Manager-Waiter");
		waitingThread.setDaemon(true);
		waitingThread.start();
	}
}
