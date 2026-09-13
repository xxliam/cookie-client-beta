package xxliam.cookieclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import xxliam.cookieclient.modules.impl.movement.Scaffold;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.utils.animation.SpringAnimation;
import xxliam.cookieclient.utils.game.MoveUtility;

import java.awt.Color;
import java.util.Locale;

/**
 * ScaffoldHud：DynamicIsland 内的搭路状态（方块数 / 放置速度 / 进度条）。
 * <p>
 * 照搬 OpenZen {@code shit.zen.hud.ScaffoldHud}：Size(260,30)、图标起笔 iconX=x+8、
 * 进度条由 {@link #progressAnim} 弹簧驱动、紫色进度 (153,0,255)、背景 (30,30,30)，
 * 文字用 poppinsBold14 / poppinsMedium10（zen 原始视觉字号），visible=Scaffold 开启。
 * <p>
 * 尺寸：字号与全部布局常量均乘 {@link IslandMetrics#scale()} 后<b>原生栅格化</b>。
 * 唯一例外是方块图标 —— {@code GuiGraphics.renderItem} 由原版按固定 16×16 GUI 单位绘制、
 * 不随字号变化，故在其外层临时套一层 {@code scale(S)}（块内坐标回到 zen 原始单位）。
 */
public class ScaffoldHud implements IHudElement {

    /** 灵动岛整体缩放（zen 原始单位 → 目标单位）；由 Size 滑条驱动，每帧 refreshScale() 刷新。 */
    private static float S;

    static {
        refreshScale();
    }

    /** 按当前大小档位刷新缩放（字号在 blockCountFont/speedFont 里即时取用，无需缓存）。 */
    private static void refreshScale() {
        S = IslandMetrics.scale();
    }

    private static final CustomFont blockCountFont() {
        return FontStore.poppinsBold(14.0f * S);
    }

    private static final CustomFont speedFont() {
        return FontStore.poppinsMedium(10.0f * S);
    }

    private final SpringAnimation progressAnim = new SpringAnimation(250.0f, 1.0f, 22.0f, 0.0f);
    private long lastUpdateTime = 0L;

    @Override
    public boolean hasBackground() {
        return true;
    }

    @Override
    public Alignment alignment() {
        return Alignment.CENTER;
    }

    @Override
    public boolean isVisible() {
        return Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled();
    }

    @Override
    public Size size() {
        refreshScale();
        return new Size(260.0f * S, 30.0f * S);
    }

    @Override
    public void render(GuiGraphics guiGraphics, float x, float y, float width, float height, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || alpha <= 0.01f) {
            return;
        }
        refreshScale();
        ItemStack blockItem = getBlockItem();
        if (blockItem.isEmpty()) {
            return;
        }
        float iconSize = height - 16.0f * S;
        float iconX = x + 8.0f * S;
        float iconY = y + 8.0f * S;
        // 方块图标：renderItem 固定 16×16 GUI 单位，故外层套 S 缩放；块内回到 zen 单位
        //（+2 落点、iconSize-4 作第 5 参 —— 与旧版逐字一致）
        if (alpha > 0.1f && iconSize - 4.0f * S > 0.0f) {
            var iconPose = guiGraphics.pose();
            iconPose.pushPose();
            iconPose.translate(iconX + 2.0f * S, iconY + 2.0f * S, 0.0f);
            iconPose.scale(S, S, 1.0f);
            guiGraphics.renderItem(blockItem, 0, 0, 0, (int) (iconSize / S) - 4);
            iconPose.popPose();
        }

        // ---- 进度条 ----
        int blockCount = blockItem.getCount();
        String countText = blockCount + " blocks";
        double speedBps = MoveUtility.getBlocksPerSecond();
        String speedText = String.format(Locale.ROOT, "%.2fb/s", speedBps);
        CustomFont blockCountFont = blockCountFont();
        CustomFont speedFont = speedFont();
        float countWidth = blockCountFont.getStringWidth(countText);
        float speedWidth = speedFont.getStringWidth(speedText);
        float maxTextWidth = Math.max(countWidth, speedWidth);
        float barWidth = width - iconSize - maxTextWidth - 32.0f * S;
        float barHeight = 6.0f * S;
        float barX = x + 8.0f * S + iconSize + 8.0f * S;
        float barY = y + height / 2.0f - barHeight / 2.0f;
        float progressPct = Math.min(1.0f, (float) blockCount / 64.0f);
        updateProgress(progressPct);

        ZenHudDraw.drawRoundedRect(guiGraphics.pose(), barX, barY, barWidth, barHeight, barHeight / 2.0f,
                colorWithAlpha(new Color(30, 30, 30).getRGB(), alpha));
        if (progressAnim.getValue() > 0.0f) {
            ZenHudDraw.drawRoundedRect(guiGraphics.pose(), barX, barY, barWidth * progressAnim.getValue(), barHeight,
                    barHeight / 2.0f, colorWithAlpha(new Color(153, 0, 255).getRGB(), alpha));
        }

        // ---- 文字（zen：count 上 / speed 下，围绕中线） ----
        float textX = barX + barWidth + 8.0f * S;
        float centerY = y + height / 2.0f;
        float countX = textX + (maxTextWidth - countWidth) / 2.0f;
        float speedX = textX + (maxTextWidth - speedWidth) / 2.0f;
        float countBaseline = centerY - ZenHudDraw.capHeight(blockCountFont) / 2.0f + 2.0f * S;
        ZenHudDraw.drawBaseline(guiGraphics.pose(), blockCountFont, countText, countX, countBaseline,
                colorWithAlpha(Color.WHITE.getRGB(), alpha));
        float speedBaseline = centerY + ZenHudDraw.capHeight(speedFont) / 2.0f + 8.0f * S;
        ZenHudDraw.drawBaseline(guiGraphics.pose(), speedFont, speedText, speedX, speedBaseline,
                colorWithAlpha(Color.GRAY.getRGB(), alpha));
    }

    /** 首个数据立即就位，其后由弹簧动画推进（照搬 zen setX）。 */
    private void updateProgress(float progressPct) {
        long now = System.currentTimeMillis();
        if (lastUpdateTime == 0L || now - lastUpdateTime > 1000L) {
            lastUpdateTime = now;
            progressAnim.setValue(progressPct);
            progressAnim.setTargetValue(progressPct);
            return;
        }
        float deltaSec = (float) (now - lastUpdateTime) / 1000.0f;
        if (deltaSec <= 0.0f) {
            return;
        }
        lastUpdateTime = now;
        progressAnim.setTargetValue(progressPct);
        progressAnim.update(deltaSec);
    }

    private ItemStack getBlockItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof BlockItem) {
            return mainHand;
        }
        for (int i = 0; i < 9; ++i) {
            ItemStack slotItem = mc.player.getInventory().getItem(i);
            if (slotItem.getItem() instanceof BlockItem) {
                return slotItem;
            }
        }
        return ItemStack.EMPTY;
    }
}
