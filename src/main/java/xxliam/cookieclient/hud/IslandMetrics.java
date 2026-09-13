package xxliam.cookieclient.hud;

/**
 * 灵动岛统一度量（固定尺寸）。
 * <p>
 * zen 原版的水印/HUD 元素全部按「原始视觉尺寸」布局（字号 36/24/20/16/14/12/10/8/6、
 * 偏移 8/10/12/48…）。早先为了让岛变小，用的是 pose 等比缩小（ISLAND_SCALE）——代价是：
 * 字形图谱按原字号栅格化（{@code fontSize × max(2, guiScale×2)} 像素），却被显示成
 * {@code fontSize × SCALE}，双线性下采样后字明显发虚。
 * <p>
 * 现在改为<b>按目标字号原生栅格化</b>：
 * <ul>
 *   <li>字体取 {@code 原始视觉字号 × }{@link #scale()}（{@link xxliam.cookieclient.render.FontStore}
 *       的 poppinsXxx / materialIcons 按视觉字号加载并池化）；</li>
 *   <li>元素的每个<b>硬编码布局常量</b>同样 {@code × scale()}（文字自身的宽度/行高等由字体
 *       度量自动跟随，无需处理）；</li>
 *   <li>{@code DynamicIsland} 的 pose 缩放恒为 1。</li>
 * </ul>
 * 于是图谱分辨率与显示尺寸 1:1，锐利度与客户端其它 UI 文本一致。
 * <p>
 * <b>尺寸是固定值</b>（曾经做成 50%~100% 的滑条，后按需求撤掉）：唯一旋钮就是 {@link #SCALE}，
 * 改它，字号与全部布局会同步等比变化。各元素仍保留 {@code refreshScale()} 结构
 * （从 {@link #scale()} 取值），以便日后重新引入可调尺寸时只需改这里。
 * <p>
 * 例外：{@code GuiGraphics.renderItem} / {@code blit} 这类由原版按固定 GUI 单位绘制的调用
 * （ScaffoldHud 的方块图标），需要在其外层临时套一层
 * {@code scale(scale())} —— 它们不是文字，不受字号影响。
 */
public final class IslandMetrics {

    /** 灵动岛缩放（相对 zen 原始视觉尺寸）。0.3917 = 0.4352 再缩 10%。 */
    public static final float SCALE = 0.3917f;

    private IslandMetrics() {
    }

    /** 当前生效缩放（当前为固定值；各元素统一经此取用，便于日后改回可调）。 */
    public static float scale() {
        return SCALE;
    }
}
