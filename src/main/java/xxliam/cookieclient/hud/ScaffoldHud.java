package xxliam.cookieclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import xxliam.cookieclient.modules.impl.movement.Scaffold;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.utils.animation.SmoothAnimationTimer;
import xxliam.cookieclient.utils.math.Easing;
import xxliam.cookieclient.utils.math.Easings;
import xxliam.cookieclient.utils.render.ThemeHelper;

/**
 * ScaffoldHud：DynamicIsland 内的搭路状态（方块数 / 进度条）。
 * <p>
 * 照搬 OpenZen {@code shit.zen.hud.ScaffoldHud} 改造：Size(260,30)、进度条由
 * {@link #progressAnim} 弹簧驱动、填充色为 Theme 主题色（原紫色 (153,0,255) 已按需求改）、
 * 轨道底色随明暗主题（{@link ThemeHelper#shade}）；方块图标、放置速度（bps）文字
 * 均已按需求删除，只剩「N blocks」一行（cap 盒中心对齐岛中线），
 * 文字用 poppinsBold14（zen 原始视觉字号），visible=Scaffold 开启。
 * <p>
 * 尺寸：字号与全部布局常量均乘 {@link IslandMetrics#scale()} 后<b>原生栅格化</b>。
 */
public class ScaffoldHud implements IHudElement {

    /** 灵动岛整体缩放（zen 原始单位 → 目标单位）；由 Size 滑条驱动，每帧 refreshScale() 刷新。 */
    private static float S;

    static {
        refreshScale();
    }

    /** 按当前大小档位刷新缩放（字号在 blockCountFont 里即时取用，无需缓存）。 */
    private static void refreshScale() {
        S = IslandMetrics.scale();
    }

    private static final CustomFont blockCountFont() {
        return FontStore.poppinsBold(14.0f * S);
    }

    /**
     * 进度条补间：**点对点曲线**（原 zen 的弹簧 {@code (250,1,22,0)} 阻尼不足会过冲，
     * 填充宽度算出来会超过轨道本身）。时长取 ≈ 原弹簧到达稳定的耗时，速度观感不变、无回弹。
     */
    private static final double PROGRESS_DURATION = 0.3;
    private static final Easing PROGRESS_EASING = Easings.EASE_OUT_POW3;

    private final SmoothAnimationTimer progressAnim = new SmoothAnimationTimer();
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

        // ---- 进度条 + 方块数（bps 文字已按需求删除） ----
        int blockCount = blockItem.getCount();
        String countText = blockCount + " blocks";
        CustomFont blockCountFont = blockCountFont();
        float countWidth = blockCountFont.getStringWidth(countText);
        // 方块图标与 bps 文字均已删除：进度条从左内边距直接开始，右端让给方块数
        float barWidth = width - countWidth - 24.0f * S;
        float barHeight = 6.0f * S;
        float barX = x + 8.0f * S;
        float barY = y + height / 2.0f - barHeight / 2.0f;
        float progressPct = Math.min(1.0f, (float) blockCount / 64.0f);
        updateProgress(progressPct);

        ZenHudDraw.drawRoundedRect(guiGraphics.pose(), barX, barY, barWidth, barHeight, barHeight / 2.0f,
                ThemeHelper.shade(0x1E1E1E, alpha));
        float fill = progressAnim.getValueF();
        if (fill > 0.0f) {
            // 填充色 = Theme 主题色（原 zen 紫色 (153,0,255) 已按需求替换）
            ZenHudDraw.drawRoundedRect(guiGraphics.pose(), barX, barY, barWidth * fill, barHeight,
                    barHeight / 2.0f, colorWithAlpha(ThemeHelper.getThemeColors()[0], alpha));
        }

        // ---- 文本（只剩方块数一行，cap 盒中心对齐岛中线后整体再上抬一点点） ----
        float textX = barX + barWidth + 8.0f * S;
        float centerY = y + height / 2.0f;
        float countBaseline = centerY + ZenHudDraw.ascent(blockCountFont)
                - ZenHudDraw.capHeight(blockCountFont) / 2.0f - 1.5f * S;
        ZenHudDraw.drawBaseline(guiGraphics.pose(), blockCountFont, countText, textX, countBaseline,
                ThemeHelper.foreground(alpha));
    }

    /** 首个数据立即就位；其后由曲线补间推进（每秒最多刷新一次的采样节流照搬 zen）。 */
    private void updateProgress(float progressPct) {
        long now = System.currentTimeMillis();
        if (lastUpdateTime == 0L || now - lastUpdateTime > 1000L) {
            // 首个数据 / 距上次刷新超过 1s（刚开搭路或严重掉帧）：直接就位，不播补间
            lastUpdateTime = now;
            progressAnim.reset(progressPct);
            return;
        }
        lastUpdateTime = now;
        progressAnim.animate(progressPct, PROGRESS_DURATION, PROGRESS_EASING);
        progressAnim.tick();
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
