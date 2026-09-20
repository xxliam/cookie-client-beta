package xxliam.cookieclient.modules.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.game.EntityUtil;
import xxliam.cookieclient.utils.math.MathUtil;
import xxliam.cookieclient.utils.rotation.Rotation;
import xxliam.cookieclient.utils.rotation.RotationUtil;

import java.util.List;

/**
 * AimAssist：瞄准辅助（整体照搬 OpenZen {@code shit.zen.modules.impl.misc.AimAssist}）。
 * <p>
 * 每帧在候选目标里挑「yaw 偏差在 Fov 内 + 距离最近」的一个，再把视角按
 * {@code 1 / Smooth amount} 的比例朝目标推进（不瞬移），并叠加两项「像人手动」的噪声：
 * yaw / pitch 各自的随机抖动；{@code Adaptive} 打开时按 A/D 方向额外偏移
 * {@code Adaptive Offset}（模拟压枪时的手部惯性）。
 * <p>
 * <b>目标范围</b>：zen 只遍历 {@code mc.level.players()}（仅玩家）。此处扩为三类，
 * 各带开关（默认全开）：{@code Players} / {@code Monsters}（{@code Enemy}）/
 * {@code Animals}（{@code Animal} + {@code WaterAnimal} + {@code AmbientCreature}）——
 * 详见 {@link #isCategoryEnabled}。候选通过一次 Range 大小的 AABB 查询取得。
 * <p>
 * 姿态细节：当视线不在目标的「脚→眼」竖直区间内时，取更近的那一端按同样比例推进，
 * 否则只推 yaw 并保持当前 pitch —— 即「先把纵向拉回身体范围，再横向追踪」。
 * 应用旋转前一律经 {@code Rotation.snapToSensitivity} 吸附到鼠标灵敏度步长。
 * <p>
 * <b>与 zen 的差异</b>：
 * <ol>
 *   <li><b>修正了一处 zen 自身的 bug</b>：原 {@code getCurrentRotation()} 写的是
 *       {@code new Rotation(mc.player.getRotationVector().y, mc.player.getRotationVector().x)}，
 *       而 {@code getRotationVector()} 返回的是<b>视图方向分量（∈[-1,1]）</b>、不是角度：
 *       视图向量的 y = −sin(pitch)、x = −sin(yaw)·cos(pitch)，拿它们当选 {@code currentRot} 后，
 *       {@code targetYaw = currentRot.yaw + (目标yaw − currentRot.yaw) / smooth} 就退化成
 *       「每帧把视角钉在 ≈ 目标yaw / smooth」。默认 smooth=15 时视角会被锁死在 6° 左右
 *       且不再靠近目标（pitch 同理），同时无条件覆盖玩家鼠标 —— 模块等于不工作。
 *       此处改为取真实的 {@code getYRot() / getXRot()}，其余公式一字未改。</li>
 *   <li>补了 {@code mc.screen != null} 早退：HUD 层在打开任何界面时仍会渲染
 *       （1.20.1 {@code GameRenderer.render} 里面板与 HUD 是两条独立路径），
 *       不加守卫会在 ClickGUI / 聊天框里强制转动视角。</li>
 *   <li>未移植 zen 的 {@code targetOffset} / {@code aimOffset} / {@code isPitchAdjusting}
 *       三个字段及其赋值：全库无人读取（该模块被裁剪过），连带
 *       {@code closest.getPosition(frameTime)} / {@code playerEye} 等只为它们服务的计算。</li>
 *   <li>目标筛选里的 {@code KillAura.INSTANCE.isValidTarget} 换成
 *       {@link EntityUtil#isValidTarget}（存活 + 非好友）+ 盔甲架/隐形排除 ——
 *       cookie 的 KillAura 仍是骨架，且没有 AntiBots / Teams 子系统。</li>
 *   <li>目标范围由「仅玩家」扩为「玩家 / 动物 / 敌对生物」三类 + 三个开关（zen 只遍历玩家）。</li>
 *   <li>{@code Range} 上限由 zen 的 30 收到 10（3~10）：30 格太远，会隔着半个视野去抢准星。</li>
 * </ol>
 */
public class AimAssist extends Module {

    public static AimAssist INSTANCE;

    private final NumberSetting randomYawOffset = new NumberSetting("Random Yaw Offset", 2, 0, 10, 0.01);
    private final NumberSetting randomPitchOffset = new NumberSetting("Random Pitch Offset", 0.075f, 0, 1, 0.01);
    private final NumberSetting range = new NumberSetting("Range", 5, 3, 10, 0.1);
    private final NumberSetting fov = new NumberSetting("Fov", 120, 1, 360, 1);
    private final BooleanSetting targetPlayers = new BooleanSetting("Players", true);
    private final BooleanSetting targetAnimals = new BooleanSetting("Animals", true);
    private final BooleanSetting targetMonsters = new BooleanSetting("Monsters", true);
    private final BooleanSetting mouseDown = new BooleanSetting("Mouse down", true);
    private final BooleanSetting adaptive = new BooleanSetting("Adaptive", true);
    private final NumberSetting adaptiveOffset = new NumberSetting("Adaptive Offset", 3, 0.1f, 15.0f, 0.01);
    private final NumberSetting smoothAmount = new NumberSetting("Smooth amount", 15, 1.0f, 90.0f, 0.1);
    private final BooleanSetting breakBlock = new BooleanSetting("Break Block", true);

    public AimAssist() {
        super("AimAssist", Category.MISC);
        INSTANCE = this;
        addSetting(randomYawOffset);
        addSetting(randomPitchOffset);
        addSetting(range);
        addSetting(fov);
        addSetting(targetPlayers);
        addSetting(targetAnimals);
        addSetting(targetMonsters);
        addSetting(mouseDown);
        addSetting(adaptive);
        addSetting(adaptiveOffset);
        addSetting(smoothAmount);
        addSetting(breakBlock);
    }

    /**
     * 每帧执行（对应 zen 的 {@code Render2DEvent}）。仅模块启用时由 {@code GuiMixin} 调用。
     */
    @Override
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        double range = this.range.getValue().doubleValue();
        boolean mouseDownOnly = this.mouseDown.getValue();
        double fov = this.fov.getValue().doubleValue();
        double randYaw = this.randomYawOffset.getValue().doubleValue();
        double randPitch = this.randomPitchOffset.getValue().doubleValue();
        boolean adaptive = this.adaptive.getValue();
        double adaptiveOff = this.adaptiveOffset.getValue().doubleValue();
        double smooth = this.smoothAmount.getValue().doubleValue();
        boolean breakBlock = this.breakBlock.getValue();

        // 只在按住左键时辅助
        if (mouseDownOnly && !mc.options.keyAttack.isDown()) {
            return;
        }
        // 左键正对着方块（挖矿）时不抢视角
        if (breakBlock && mc.options.keyAttack.isDown()
                && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return;
        }

        LocalPlayer localPlayer = mc.player;
        Vec3 eye = localPlayer.position().add(0.0, localPlayer.getEyeHeight(), 0.0);
        Rotation currentRot = currentRotation();

        // zen 原文是 mc.level.players()（只遍历玩家）。改为按 Range 做一次 AABB 查询、
        // 用 isValidTarget 里的开关筛「玩家 / 动物 / 敌对生物」——见类注释。
        AABB area = localPlayer.getBoundingBox().inflate(range);
        List<LivingEntity> candidates = mc.level.getEntitiesOfClass(LivingEntity.class, area, this::isValidTarget);

        LivingEntity closest = null;
        float bestDist = Float.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (candidate == null || candidate == localPlayer) {
                continue;
            }
            float eyeHeight = candidate.getEyeHeight();
            Rotation eyeRot = RotationUtil.normalizeRotation(currentRot.negate().subtract(
                    RotationUtil.rotationTo(eye, candidate.position().add(0.0, eyeHeight, 0.0)).negate()));
            if (eyeRot.getYaw() < 0.0f) {
                eyeRot.setYaw(-eyeRot.getYaw());
            }
            if (eyeRot.getPitch() < 0.0f) {
                eyeRot.setPitch(-eyeRot.getPitch());
            }
            float dist = (float) localPlayer.position().subtract(candidate.position()).length();
            if (Math.abs(eyeRot.getYaw()) <= fov && dist <= range && bestDist > dist) {
                closest = candidate;
                bestDist = dist;
            }
        }
        if (closest == null || !isValidTarget(closest)) {
            return;
        }

        Vec3 closestPos = closest.position();
        float closestEyeHeight = closest.getEyeHeight();
        Vec3 closestEye = closestPos.add(0.0, closestEyeHeight, 0.0);
        Rotation rotToFeet = RotationUtil.rotationTo(eye, closestPos);
        Rotation rotToEye = RotationUtil.rotationTo(eye, closestEye);
        Rotation rotEyeDelta = RotationUtil.normalizeRotation(currentRot.negate().subtract(rotToEye.negate()));
        Rotation rotFeetDelta = RotationUtil.normalizeRotation(currentRot.negate().subtract(rotToFeet.negate()));

        double yawOffset = MathUtil.randomDouble(-randYaw, randYaw);
        if (adaptive) {
            if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
                yawOffset -= adaptiveOff;
            }
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
                yawOffset += adaptiveOff;
            }
        }

        float targetYaw = currentRot.getYaw() + (float) (((double) rotEyeDelta.getYaw() + yawOffset) / smooth);
        if (currentRot.getPitch() > rotToFeet.getPitch() || currentRot.getPitch() < rotToEye.getPitch()) {
            // 视线落在目标的「脚→眼」区间之外：纵向往更近的一端收
            float feetPitch = currentRot.getPitch() + (float) (rotFeetDelta.getPitch() / smooth);
            float eyePitch = currentRot.getPitch() + (float) (rotEyeDelta.getPitch() / smooth);
            float feetDiff = Math.abs(currentRot.getPitch() - feetPitch);
            float eyeDiff = Math.abs(currentRot.getPitch() - eyePitch);
            float targetPitch = feetDiff > eyeDiff ? eyePitch : feetPitch;
            applyRotation(new Rotation(targetYaw,
                    targetPitch + (float) MathUtil.randomDouble(-randPitch, randPitch)));
        } else {
            // 已在竖直区间内：只推 yaw，pitch 保持当前值（仅叠加随机抖动）
            applyRotation(new Rotation(targetYaw,
                    currentRot.getPitch() + (float) MathUtil.randomDouble(-randPitch, randPitch)));
        }
    }

    /**
     * 当前视角。
     * <p>
     * zen 原文是 {@code new Rotation(getRotationVector().y, getRotationVector().x)} ——
     * 那是视图方向分量而非角度，会让视角被钉死，见类注释的修正说明。
     */
    private Rotation currentRotation() {
        Minecraft mc = Minecraft.getInstance();
        return new Rotation(mc.player.getYRot(), mc.player.getXRot());
    }

    /** 应用旋转（照搬 zen {@code applyRotation}，含灵敏度步长吸附与 pitch 钳制）。 */
    private void applyRotation(Rotation rotation) {
        Minecraft mc = Minecraft.getInstance();
        rotation.snapToSensitivity(mc.options.sensitivity().get().floatValue());
        float yaw = rotation.getYaw();
        float pitch = rotation.getPitch();
        mc.player.setXRot(pitch);
        mc.player.setYRot(yaw);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot(), -90.0f, 90.0f));
        mc.player.xRotO = pitch;
        mc.player.yRotO = yaw;
        mc.player.xRotO = Mth.clamp(mc.player.xRotO, -90.0f, 90.0f);
    }

    /**
     * 目标筛选（zen 原为 {@code KillAura.INSTANCE.isValidTarget}）。
     * <p>
     * cookie 的 KillAura 仍是骨架、也没有 AntiBots / Teams 子系统，故用
     * {@link EntityUtil#isValidTarget}（存活 + 非好友）承接那部分，并补上 zen
     * {@code isValidTarget} 同样会排除的「盔甲架 / 隐形实体」。
     * <p>
     * 目标类别由三个开关决定（{@link #isCategoryEnabled}）—— zen 只遍历玩家，
     * 这里扩展为玩家 / 动物 / 敌对生物三类。
     */
    public boolean isValidTarget(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == null || entity == mc.player) {
            return false;
        }
        if (!isCategoryEnabled(entity)) {
            return false;
        }
        if (mc.player.distanceTo(entity) > this.range.getValue().floatValue()) {
            return false;
        }
        if (!EntityUtil.isValidTarget(entity)) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (entity.isInvisible()) {
            return false;
        }
        if (!mc.player.hasLineOfSight(entity)) {
            return false;
        }
        return !entity.isDeadOrDying() && !(entity.getHealth() <= 0.0f);
    }

    /**
     * 目标类别开关：
     * <ul>
     *   <li><b>Players</b> —— {@code Player}；</li>
     *   <li><b>Monsters</b> —— {@code Enemy}（僵尸/骷髅/苦力怕/蜘蛛/史莱姆/恶魂/末影龙…，
     *       比 {@code Monster} 更全）；</li>
     *   <li><b>Animals</b> —— {@code Animal}（牛羊猪鸡狼猫马…）+ {@code WaterAnimal}
     *       （鱿鱼/鱼）+ {@code AmbientCreature}（蝙蝠）。</li>
     * </ul>
     * 其余生物（村民、铁傀儡、雪傀儡、潜影贝、凋灵等）三类都不属于，恒不纳入。
     */
    private boolean isCategoryEnabled(LivingEntity entity) {
        if (entity instanceof Player) {
            return targetPlayers.getValue();
        }
        if (entity instanceof Enemy) {
            return targetMonsters.getValue();
        }
        if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) {
            return targetAnimals.getValue();
        }
        return false;
    }

    /** 后缀 = 攻击范围（整数省小数，如 5 / 5.5）。 */
    @Override
    public String getSuffix() {
        float r = range.getValue().floatValue();
        return r == Math.floor(r) ? String.valueOf((int) r) : String.valueOf(r);
    }
}
