package xxliam.cookieclient.modules.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.modules.impl.render.esp.OpalEspRenderer;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.settings.impl.MultiSelectSetting;

/**
 * ESP：实体透视。两个模式：
 * <ul>
 *   <li>{@code Glow}：实体发光（由 {@code MinecraftMixin} 调用 {@link #isGlowing}）；</li>
 *   <li>{@code Opal}：完整移植 OpenOpal {@code ESPModule} —— 2D 外框(可选黑描边)、
 *       左缘血条、名牌(Name/Health/Distance/Equipment+附魔)与状态指示器，
 *       渲染与目标过滤语义全部照搬 opal（见 {@link OpalEspRenderer}）。</li>
 * </ul>
 */
public class ESP extends Module {

    public static ESP INSTANCE;

    private final ModeSetting modeSetting = new ModeSetting("Mode", "Glow", "Opal").withDefault("Opal");

    // ---- Glow 模式设置 ----
    private final BooleanSetting playersSetting = new BooleanSetting("Players", true);
    private final BooleanSetting mobsSetting = new BooleanSetting("Mobs", false);
    private final BooleanSetting animalsSetting = new BooleanSetting("Animals", false);
    private final BooleanSetting itemsSetting = new BooleanSetting("Items", false);
    private final BooleanSetting arrowsSetting = new BooleanSetting("Arrows", true);

    // ---- Opal 模式设置（照搬 opal ESPSettings；仅 Opal 档位可见） ----
    private final BooleanSetting opalBox = new BooleanSetting("Opal Box", true, this::opalModeView);
    private final BooleanSetting opalBoxStroke = new BooleanSetting("Opal Box Stroke", true,
            () -> opalModeView() && opalBox.getValue());
    private final BooleanSetting opalHealthBar = new BooleanSetting("Opal Health Bar", true, this::opalModeView);
    private final BooleanSetting opalHealthBarStroke = new BooleanSetting("Opal Health Bar Stroke", true,
            () -> opalModeView() && opalHealthBar.getValue());
    private final BooleanSetting opalNameTags = new BooleanSetting("Opal Name Tags", true, this::opalModeView);
    private final MultiSelectSetting opalNameTagElements = new MultiSelectSetting("Opal Name Tag Elements",
            "Name", "Health", "Distance", "Equipment")
            .withDefaults("Name", "Health", "Distance")
            .withVisibility(() -> opalModeView() && opalNameTags.getValue());
    private final MultiSelectSetting opalNameTagIndicators = new MultiSelectSetting("Opal Name Tag Indicators",
            "Sneaking", "Strength", "Invisible", "Blocking")
            .withDefaults("Sneaking", "Strength", "Invisible", "Blocking")
            .withVisibility(() -> opalModeView() && opalNameTags.getValue());

    // ---- Opal 目标过滤（对齐 opal TargetProperty 默认：玩家 + 本地 + Friendly，Hostile/Passive 关） ----
    private final BooleanSetting opalPlayers = new BooleanSetting("Opal Players", true, this::opalModeView);
    private final BooleanSetting opalLocalPlayer = new BooleanSetting("Opal Local Player", true,
            () -> opalModeView() && opalPlayers.getValue());
    private final BooleanSetting opalHostile = new BooleanSetting("Opal Hostile", false, this::opalModeView);
    private final BooleanSetting opalPassive = new BooleanSetting("Opal Passive", false, this::opalModeView);
    private final BooleanSetting opalFriendly = new BooleanSetting("Opal Friendly", true, this::opalModeView);

    public ESP() {
        super("ESP", Category.RENDER);
        INSTANCE = this;
        // Glow 目标设置在 Opal 档位下隐藏
        playersSetting.setVisibility(this::glowModeView);
        mobsSetting.setVisibility(this::glowModeView);
        animalsSetting.setVisibility(this::glowModeView);
        itemsSetting.setVisibility(this::glowModeView);
        arrowsSetting.setVisibility(this::glowModeView);
        addSetting(modeSetting);
        addSetting(playersSetting);
        addSetting(mobsSetting);
        addSetting(animalsSetting);
        addSetting(itemsSetting);
        addSetting(arrowsSetting);
        addSetting(opalBox);
        addSetting(opalBoxStroke);
        addSetting(opalHealthBar);
        addSetting(opalHealthBarStroke);
        addSetting(opalNameTags);
        addSetting(opalNameTagElements);
        addSetting(opalNameTagIndicators);
        addSetting(opalPlayers);
        addSetting(opalLocalPlayer);
        addSetting(opalHostile);
        addSetting(opalPassive);
        addSetting(opalFriendly);
    }

    /** 是否处于 Opal 档位（与模块开关无关，仅看 Mode 值，供 ClickGUI 显隐）。 */
    public boolean opalModeView() {
        return "Opal".equalsIgnoreCase(modeSetting.getValue());
    }

    /** 是否处于 Glow 档位。 */
    private boolean glowModeView() {
        return !opalModeView();
    }

    /** 模块开启且处于 Opal 档位。 */
    public boolean isOpalModeActive() {
        return isEnabled() && opalModeView();
    }

    /** Glow 模式：判断实体是否应发光（由 MinecraftMixin 调用）。 */
    public boolean isGlowing(Entity entity) {
        if (isEnabled() && "Glow".equalsIgnoreCase(modeSetting.getValue())) {
            if (entity instanceof Player && playersSetting.getValue()) return true;
            if (entity instanceof Animal && animalsSetting.getValue()) return true;
            if (entity instanceof Mob && mobsSetting.getValue()) return true;
            if (entity instanceof ItemEntity && itemsSetting.getValue()) return true;
            return entity instanceof Arrow && arrowsSetting.getValue();
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Opal 档位：对渲染器与「原版名牌抑制 Mixin」暴露的统一判定
    // ---------------------------------------------------------------

    public boolean areOpalBoxEnabled() {
        return opalBox.getValue();
    }

    public boolean areOpalBoxStrokeEnabled() {
        return opalBoxStroke.getValue() && opalBox.getValue();
    }

    public boolean areOpalHealthBarEnabled() {
        return opalHealthBar.getValue();
    }

    public boolean areOpalHealthBarStrokeEnabled() {
        return opalHealthBarStroke.getValue() && opalHealthBar.getValue();
    }

    public boolean areOpalNameTagsEnabled() {
        return opalNameTags.getValue();
    }

    public MultiSelectSetting opalNameTagElements() {
        return opalNameTagElements;
    }

    public MultiSelectSetting opalNameTagIndicators() {
        return opalNameTagIndicators;
    }

    /**
     * 该实体是否会被 Opal 档位渲染（目标过滤 + 本地玩家透视规则，照搬 opal：
     * 第一人称始终跳过自己；玩家受 Players/Friendly(同队色) 约束；非玩家按 Hostile/Passive）。
     */
    public boolean opalMatchesTarget(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (entity == mc.player) {
            if (mc.options.getCameraType().isFirstPerson()) {
                return false;
            }
            return opalLocalPlayer.getValue();
        }
        if (entity instanceof Player player) {
            if (!opalPlayers.getValue()) {
                return false;
            }
            // 同队（含双方无队 -> 颜色相等）在 Friendly 关闭时排除（照搬 opal areOnSameTeam）
            if (!opalFriendly.getValue() && player.getTeamColor() == mc.player.getTeamColor()) {
                return false;
            }
            return true;
        }
        boolean hostile = entity instanceof Monster;
        if (hostile) {
            return opalHostile.getValue();
        }
        return opalPassive.getValue();
    }

    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        if (isOpalModeActive()) {
            OpalEspRenderer.render(guiGraphics, partialTicks, this);
        }
        // Glow 档不需要 HUD 渲染：发光由 MinecraftMixin（shouldEntityAppearGlowing → isGlowing）驱动
    }
}
