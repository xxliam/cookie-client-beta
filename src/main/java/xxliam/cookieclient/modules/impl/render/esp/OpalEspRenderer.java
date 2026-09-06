package xxliam.cookieclient.modules.impl.render.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import xxliam.cookieclient.modules.impl.render.ESP;
import xxliam.cookieclient.render.CustomFont;
import xxliam.cookieclient.render.FontStore;
import xxliam.cookieclient.render.Renderer;
import xxliam.cookieclient.utils.game.EntityUtil;
import xxliam.cookieclient.utils.render.ColorUtil;
import xxliam.cookieclient.utils.render.ProjectionUtil;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Opal 风格 ESP 的屏幕空间渲染器（照搬 OpenOpal {@code ESPModule + ESPUtility} 的几何/参数）。
 * <p>
 * 定位（box 矩形）：采用 {@link ProjectionUtil}（zen 式相机共轭投影，实机已验证）逐角点投影；
 * 计算盒由 {@link EntityUtil#getInterpolatedAABB} 插值得到，再叠加 opal 头部 padding（蹲伏 0.1 / 其它 0.2）。
 * <p>
 * 渲染参数（0.5 边框、stroke 3 倍宽、名牌 padding 2 / 圆角 2 / 高 9 / 图标偏移、装备 0.65/0.6 矩阵序列、
 * 附魔缩写表）逐值照搬 opal；Box / 血条 / 名牌 / 装备 / 状态指示器元素位置全部对齐 opal 原版。
 */
public final class OpalEspRenderer {

    private static final DecimalFormat HEALTH_DF = new DecimalFormat("0.#");

    /** 名牌字号（opal：5px，用 cookie loadFont 语义 => 视觉字号 ×2 传入）。 */
    private static final CustomFont NAME_TAG_FONT = FontStore.PRODUCTSANS_BOLD_5;
    private static final CustomFont ICON_FONT = FontStore.MATERIALICONS_5;

    /** 名牌背景模糊层级（越高越模糊；近似 opal BLUR_PAINT）。 */
    private static final float BLUR_LOD = 2.5f;

    private OpalEspRenderer() {
    }

    // ---------------------------------------------------------------
    // 名牌元素模型（照搬 opal NameTagElement / NameTagIcon / NameTagIconPosition）
    // ---------------------------------------------------------------

    private enum IconPosition { LEFT, RIGHT }

    private record Icon(String unicode, IconPosition position, float horizontalOffset) {
        Icon(String unicode) {
            this(unicode, IconPosition.RIGHT, 0.5F);
        }

        Icon(String unicode, float horizontalOffset) {
            this(unicode, IconPosition.RIGHT, horizontalOffset);
        }

        Icon(String unicode, IconPosition position) {
            this(unicode, position, 0.5F);
        }
    }

    private record Element(Icon icon, String text, int color) {
        Element(Icon icon, int color) {
            this(icon, null, color);
        }

        Element(String text, int color) {
            this(null, text, color);
        }
    }

    // ---------------------------------------------------------------
    // 入口
    // ---------------------------------------------------------------

    public static void render(GuiGraphics guiGraphics, float tickDelta, ESP module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!module.opalMatchesTarget(living)) {
                continue;
            }
            ScreenRect rect = projectBox(living, tickDelta);
            if (rect == null) {
                continue;
            }
            float thickness = 0.5F;
            if (module.areOpalBoxEnabled()) {
                renderBox(rect, thickness, ColorUtil.applyOpacity(living.getTeamColor(), 1.0f));
            }
            if (module.areOpalHealthBarEnabled()) {
                renderHealthBar(guiGraphics, rect, thickness, living.getHealth() / living.getMaxHealth());
            }
            if (module.areOpalNameTagsEnabled()) {
                renderNameTag(guiGraphics, living, rect, module);
            }
        }
    }

    private record ScreenRect(float x, float y, float w, float h) {
    }

    /**
     * 把实体碰撞箱投成屏幕 2D 矩形（GUI 逻辑坐标）。
     * <p>
     * 投影复用 {@link ProjectionUtil}（zen 式相机共轭投影，已在 1.20.1 验证），盒尺寸取自
     * {@link EntityUtil#getInterpolatedAABB} 的插值 AABB，顶面在原高度基础上 +0.2（蹲伏 +0.1，
     * 照搬 opal）。角点被裁剪（camera 背后或完全在视口外）整体丢弃。
     */
    private static ScreenRect projectBox(LivingEntity entity, float tickDelta) {
        AABB aabb = EntityUtil.getInterpolatedAABB(entity, tickDelta);
        boolean sneaking = entity.isCrouching();
        float topY = (float) aabb.maxY + (sneaking ? 0.1f : 0.2f);

        Vec3[] corners = new Vec3[]{
                new Vec3(aabb.minX, aabb.minY, aabb.minZ),
                new Vec3(aabb.maxX, aabb.minY, aabb.minZ),
                new Vec3(aabb.maxX, aabb.minY, aabb.maxZ),
                new Vec3(aabb.minX, aabb.minY, aabb.maxZ),
                new Vec3(aabb.minX, topY, aabb.minZ),
                new Vec3(aabb.maxX, topY, aabb.minZ),
                new Vec3(aabb.maxX, topY, aabb.maxZ),
                new Vec3(aabb.minX, topY, aabb.maxZ)
        };

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        int count = 0;
        for (Vec3 corner : corners) {
            Vector2f p = ProjectionUtil.project(corner.x, corner.y, corner.z);
            if (p == null) {
                continue; // 相机背后 / 在 NDC 外
            }
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            count++;
        }
        if (count == 0) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        if (minX >= screenW || minY >= screenH || maxX <= 0.0f || maxY <= 0.0f) {
            return null;
        }
        float lx = Math.max(0.0f, minX);
        float ly = Math.max(0.0f, minY);
        float rx = Math.min(screenW, maxX);
        float by = Math.min(screenH, maxY);
        return new ScreenRect(lx, ly, rx - lx, by - ly);
    }

    // ---------------------------------------------------------------
    // Box / 血条（照搬 opal renderFullBox / renderHealthBar 的 NVG 几何）
    // ---------------------------------------------------------------

    private static void renderBox(ScreenRect r, float thickness, int color) {
        if (r.w() <= 0 || r.h() <= 0) {
            return;
        }
        ESP module = ESP.INSTANCE;
        if (module != null && module.areOpalBoxStrokeEnabled()) {
            float stroke = thickness * 3.0f;
            rectOutline(r.x() - thickness, r.y() - thickness, r.w() + thickness * 2, r.h() + thickness * 2, stroke, 0xff000000);
            rectOutline(r.x(), r.y(), r.w(), r.h(), thickness, color);
        } else {
            rectOutline(r.x(), r.y(), r.w(), r.h(), thickness, color);
        }
    }

    /** NVG rectOutline：上 / 右 / 下 / 左 四条细矩形。 */
    private static void rectOutline(float x, float y, float width, float height, float t, int color) {
        if (width <= 0 || height <= 0 || t <= 0) {
            return;
        }
        PoseStack pose = new PoseStack();
        Renderer.drawFilledRect(pose, x, y, width, t, color);                                    // 上
        Renderer.drawFilledRect(pose, x + width - t, y + t, t, height - t, color);                // 右
        Renderer.drawFilledRect(pose, x, y + height - t, width - t, t, color);                    // 下
        Renderer.drawFilledRect(pose, x, y + t, t, height - t, color);                            // 左
    }

    private static void renderHealthBar(GuiGraphics guiGraphics, ScreenRect r, float thickness, float healthValue) {
        float x = r.x(), y = r.y(), w = r.w(), h = r.h();
        boolean stroke = ESP.INSTANCE != null && ESP.INSTANCE.areOpalHealthBarStrokeEnabled();
        ESP module = ESP.INSTANCE;
        boolean boxStroke = module != null && module.areOpalBoxStrokeEnabled();
        if (stroke) {
            float bx = x - (thickness * 2) - 0.5F - (module != null && module.areOpalBoxEnabled() && boxStroke ? 0.5F : 0);
            rectOutline(bx, y + (h - (h * healthValue)), thickness, h * healthValue, thickness, 0xff000000, 0xff00ff00);
        } else {
            Renderer.drawFilledRect(guiGraphics.pose(), x - thickness - 0.5F - (module != null && module.areOpalBoxEnabled() && boxStroke ? 0.5F : 0),
                    y + (h - (h * healthValue)), thickness, h * healthValue, 0xff00ff00);
        }
    }

    private static void rectOutline(float x, float y, float width, float height, float t, int outlineColor, int fillColor) {
        // opal rectStroke(x,y,w,h,strokeT,fillColor,outlineColor)：外描边大一圈再填色
        rectOutline(x - t, y - t, width + t * 2, height + t * 2, t, outlineColor);
        if (width > 0 && height > 0) {
            Renderer.drawFilledRect(new PoseStack(), x, y, width, height, fillColor);
        }
    }

    // ---------------------------------------------------------------
    // NameTags（照搬 opal renderNameTag / renderNameTagElements / renderEquipment）
    // ---------------------------------------------------------------

    private static void renderNameTag(GuiGraphics g, LivingEntity entity, ScreenRect r, ESP module) {
        float x = r.x(), y = r.y(), w = r.w();
        List<Element> elements = buildElements(entity, module);
        float[] pos = calculateStartingPosition(elements, x, y, w);
        renderElements(g, elements, pos[0], pos[1]);
        if (module.opalNameTagElements().isSelected("Equipment")) {
            renderEquipment(g, entity, x, y, w, !elements.isEmpty());
        }
    }

    private static List<Element> buildElements(LivingEntity entity, ESP module) {
        List<Element> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        // Indicators
        if (module.opalNameTagIndicators().isSelected("Strength") && entity.hasEffect(MobEffects.DAMAGE_BOOST)) {
            list.add(new Element(new Icon("\uefe4", 0.25F), 0xFFFF0000));
        }
        if (module.opalNameTagIndicators().isSelected("Sneaking") && entity.isCrouching()) {
            list.add(new Element(new Icon("\uf19f"), 0xFFFF5555));
        }
        if (module.opalNameTagIndicators().isSelected("Invisible") && entity.isInvisible()) {
            list.add(new Element(new Icon("\ue8f5", 0.3F), 0xFFAAAAAA));
        }
        if (module.opalNameTagIndicators().isSelected("Blocking") && entity instanceof Player player
                && (player.isBlocking() || (player.isUsingItem() && player.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK))) {
            list.add(new Element(new Icon("\ue1d5", 0.15F), 0xFF41AF7D));
        }

        // Items / Elements
        if (module.opalNameTagElements().isSelected("Distance") && entity != mc.player) {
            list.add(new Element(new Icon("\ue55c", IconPosition.RIGHT), String.valueOf((int) Math.floor(entity.distanceTo(mc.player))), 0xFFAAAAAA));
        }
        if (module.opalNameTagElements().isSelected("Name")) {
            list.add(new Element(entity.getDisplayName() != null ? entity.getDisplayName().getString() : entity.getName().getString(), -1));
        }
        if (module.opalNameTagElements().isSelected("Health")) {
            list.add(new Element(new Icon("\uE87D", IconPosition.RIGHT), HEALTH_DF.format(entity.getHealth()), -1));
            if (entity.getAbsorptionAmount() > 0) {
                list.add(new Element(new Icon("\uE87D", IconPosition.RIGHT), HEALTH_DF.format(entity.getAbsorptionAmount()), 0xFFFFC247));
            }
        }
        return list;
    }

    /** 计算名牌起始 x（元素总宽居中于实体框上方）与 baseline y（opal: y - 4.5）。 */
    private static float[] calculateStartingPosition(List<Element> elements, float x, float y, float w) {
        float totalWidth = 0;
        for (int i = 0; i < elements.size(); i++) {
            Element element = elements.get(i);
            if (element.text() != null) {
                totalWidth += NAME_TAG_FONT.getStringWidth(element.text());
            }
            if (element.icon() != null) {
                totalWidth += ICON_FONT.getStringWidth(element.icon().unicode());
            }
            if (i < elements.size() - 1) {
                totalWidth += 5;
            }
        }
        float startX = x + w / 2.0f - totalWidth / 2.0f;
        return new float[]{startX, y - 4.5F};
    }

    private static void renderElements(GuiGraphics g, List<Element> elements, float startX, float startY) {
        float currentX = startX;
        for (Element element : elements) {
            boolean hasText = element.text() != null;
            boolean hasIcon = element.icon() != null;
            float textWidth = hasText ? NAME_TAG_FONT.getStringWidth(element.text()) : 0.0f;
            float iconWidth = hasIcon ? ICON_FONT.getStringWidth(element.icon().unicode()) : 0.0f;

            // 背景：模糊层（近似 opal BLUR_PAINT）→ 半透黑层（opal 精确第二层）
            float bgPadding = 2.0f;
            float bgRadius = 2.0f;
            float bgX = currentX - bgPadding;
            float bgY = startY - bgPadding - 4.5F;
            float bgW = textWidth + iconWidth + bgPadding * 2;
            float bgH = 5.0f + bgPadding * 2;
            Renderer.drawScreenBlur(g.pose(), bgX, bgY, bgW, bgH, bgRadius, BLUR_LOD);
            Renderer.drawRoundedRect(g.pose(), bgX, bgY, bgW, bgH, bgRadius, ColorUtil.applyOpacity(0xff000000, 0.5f));

            float textX = currentX;

            // 左图标
            if (hasIcon && element.icon().position() == IconPosition.LEFT) {
                drawBaselineIcon(g, ICON_FONT, element.icon().unicode(), currentX + element.icon().horizontalOffset(), startY + 1, element.color());
                textX += iconWidth;
            }
            // 文字
            if (hasText) {
                drawBaselineText(g, NAME_TAG_FONT, element.text(), textX, startY, element.color());
            }
            // 右图标
            if (hasIcon && element.icon().position() == IconPosition.RIGHT) {
                drawBaselineIcon(g, ICON_FONT, element.icon().unicode(), textX + textWidth + element.icon().horizontalOffset(), startY + 1, element.color());
            }
            currentX += textWidth + iconWidth + 5;
        }
    }

    /** NVG 语义是 baseline；cookie drawString 是格顶，需换算（与 Notifications.drawText 一致）。 */
    private static void drawBaselineText(GuiGraphics g, CustomFont font, String text, float x, float baselineY, int color) {
        float top = baselineY - font.getFontMetrics().getAscent() / (float) font.getScale();
        font.drawString(g.pose(), text, x, top, color);
    }

    private static void drawBaselineIcon(GuiGraphics g, CustomFont font, String icon, float x, float baselineY, int color) {
        if (icon == null || icon.isEmpty()) {
            return;
        }
        drawBaselineText(g, font, icon, x, baselineY, color);
    }

    // ---------------------------------------------------------------
    // 装备 + 附魔（照搬 opal renderEquipment 的矩阵序列）
    // ---------------------------------------------------------------

    private static void renderEquipment(GuiGraphics g, LivingEntity entity, float x, float y, float w, boolean hasNameTagElements) {
        Minecraft mc = Minecraft.getInstance();
        List<ItemStack> equipment = new ArrayList<>();
        // opal 遍历 AttributeModifierSlot.ARMOR（顺序 HEAD → CHEST → LEGS → FEET）
        EquipmentSlot[] armorOrder = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : armorOrder) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                equipment.add(stack);
            }
        }
        ItemStack mainHand = entity.getMainHandItem();
        if (!mainHand.isEmpty()) {
            equipment.add(mainHand);
        }
        if (equipment.isEmpty()) {
            return;
        }

        float scale = 0.65F;
        float stackTextScale = 0.6F;
        PoseStack pose = g.pose();
        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            float stackX = x + w / 2.0f - (equipment.size() * scale * 8) + ((equipment.size() - i - 1) * scale * 16);
            pose.pushPose();
            pose.translate(stackX, y - (hasNameTagElements ? 23.5F : 14.0F), 0.0f);
            pose.scale(scale, scale, 1.0f);
            pose.scale(stackTextScale, stackTextScale, 1.0f);
            pose.translate(6, 12, 0.0f);

            pose.pushPose();
            pose.translate(-6, -12, 0.0f);
            pose.scale(1.0f / stackTextScale, 1.0f / stackTextScale, 1.0f);
            g.renderItem(stack, 0, 0);
            pose.popPose();

            int enchantIndex = 0;
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey());
                String shortName = key == null ? null : ENCHANTMENT_SHORTS.get(key.getPath());
                if (shortName == null) {
                    continue;
                }
                g.drawString(mc.font, shortName + entry.getValue(), 2, 7 + (-8 * enchantIndex), -1, true);
                enchantIndex++;
            }
            pose.popPose();
        }
    }

    private static final Map<String, String> ENCHANTMENT_SHORTS = Map.ofEntries(
            Map.entry("protection", "Pr"), Map.entry("fire_protection", "Fp"), Map.entry("feather_falling", "Ff"),
            Map.entry("blast_protection", "Bp"), Map.entry("projectile_protection", "Pp"), Map.entry("respiration", "Re"),
            Map.entry("aqua_affinity", "Aa"), Map.entry("thorns", "Th"), Map.entry("depth_strider", "Ds"),
            Map.entry("frost_walker", "Fw"), Map.entry("binding_curse", "Bc"), Map.entry("soul_speed", "Ss"),
            Map.entry("swift_sneak", "Sn"), Map.entry("sharpness", "Sh"), Map.entry("smite", "Sm"),
            Map.entry("bane_of_arthropods", "BoA"), Map.entry("knockback", "Kb"), Map.entry("fire_aspect", "Fa"),
            Map.entry("looting", "Lo"), Map.entry("sweeping_edge", "Sw"), Map.entry("efficiency", "Ef"),
            Map.entry("silk_touch", "St"), Map.entry("unbreaking", "Un"), Map.entry("fortune", "Fo"),
            Map.entry("power", "Po"), Map.entry("punch", "Pu"), Map.entry("flame", "Fl"), Map.entry("infinity", "In"),
            Map.entry("luck_of_the_sea", "Lu"), Map.entry("lure", "Lr"), Map.entry("loyalty", "Ly"),
            Map.entry("impaling", "Ip"), Map.entry("riptide", "Ri"), Map.entry("channeling", "Ch"),
            Map.entry("multishot", "Mu"), Map.entry("quick_charge", "Qc"), Map.entry("piercing", "Pi"),
            Map.entry("mending", "Me"), Map.entry("vanishing_curse", "Vc")
    );
}