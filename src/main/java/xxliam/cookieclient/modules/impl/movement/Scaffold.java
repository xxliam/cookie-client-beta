package xxliam.cookieclient.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.RandomUtils;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.NumberSetting;
import xxliam.cookieclient.utils.game.BlockUtil;
import xxliam.cookieclient.utils.game.MotionSimulator;
import xxliam.cookieclient.utils.game.MovementUtil;
import xxliam.cookieclient.utils.game.RayTraceUtil;
import xxliam.cookieclient.utils.math.MathUtil;
import xxliam.cookieclient.utils.rotation.Rotation;
import xxliam.cookieclient.utils.rotation.RotationHandler;
import xxliam.cookieclient.utils.rotation.RotationUtil;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;

/**
 * Scaffold：自动搭路（Normal 模式）。
 * <p>
 * 旋转由 {@code LocalPlayerMixin} 在发包时应用，收包由 {@code ConnectionMixin} 驱动。
 */
public class Scaffold extends Module {

    public static Scaffold INSTANCE;

    public final BooleanSetting eagle = new BooleanSetting("Eagle", true);
    public final BooleanSetting sneak = new BooleanSetting("Sneak", true);
    public final BooleanSetting snap = new BooleanSetting("Snap", true);
    public final NumberSetting rotationTick = new NumberSetting("Rotation Tick", 3, 1, 6, 1);
    public final BooleanSetting clutch = new BooleanSetting("Clutch", true);

    public Rotation correctRotation = new Rotation();
    public Rotation rots = new Rotation();
    public Rotation lastRots = new Rotation();
    public int targetYLevel = -1;
    public int velocityDelay = 0;

    private int oldSlot;
    private PlacementTarget currentPlacement;
    private int eagleTimer;
    private int groundTicks = 0;
    private int airTicks = 0;
    private int rotationDelay = 0;
    private boolean canBuildNow;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(eagle);
        addSetting(sneak);
        addSetting(snap);
        addSetting(rotationTick);
        addSetting(clutch);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            oldSlot = mc.player.getInventory().selected;
            rots.setYawPitch(mc.player.getYRot() - 180.0f, mc.player.getXRot());
            lastRots.setYawPitch(mc.player.yRotO - 180.0f, mc.player.xRotO);
            currentPlacement = null;
            targetYLevel = 10000;
            velocityDelay = 0;
            canBuildNow = true;
        }
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            boolean jumpDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getDefaultKey().getValue());
            boolean shiftDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getDefaultKey().getValue());
            mc.options.keyJump.setDown(jumpDown);
            mc.options.keyShift.setDown(shiftDown);
            mc.options.keyUse.setDown(false);
            mc.player.getInventory().selected = oldSlot;
            canBuildNow = true;
            RotationHandler.isRotating = false;
        }
        super.onDisable();
    }

    /** 收包：检测速度包（离合器用）。由 ConnectionMixin 调用。 */
    public static void onPacketReceive(Packet<?> packet) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.getId() == mc.player.getId()) {
            double length = new Vec3(motion.getXa() / 8000.0, 0.0, motion.getZa() / 8000.0).length();
            if (length >= 1.5) {
                INSTANCE.velocityDelay = 60;
            }
        }
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 统计地面/空中 tick
        if (mc.player.onGround()) {
            airTicks = 0;
            groundTicks++;
        } else {
            groundTicks = 0;
            airTicks++;
        }
        if (velocityDelay > 0) {
            velocityDelay--;
        }
        if (mc.player.onGround() && velocityDelay <= 30) {
            velocityDelay = 0;
        }

        int placeableSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                placeableSlot = i;
                break;
            }
        }
        if (placeableSlot != -1 && mc.player.getInventory().selected != placeableSlot) {
            mc.player.getInventory().selected = placeableSlot;
        }

        boolean jumpHeld = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getDefaultKey().getValue());
        if (targetYLevel == -1
                || targetYLevel > (int) Math.floor(mc.player.getY()) - 1
                || mc.player.onGround()
                || !MovementUtil.isMoving()
                || jumpHeld) {
            targetYLevel = (int) Math.floor(mc.player.getY()) - 1;
        }

        applyRotations();
        canBuildNow = true;
        if (currentPlacement != null && placeableSlot != -1) {
            if (clutch.getValue() && mc.player.getDeltaMovement().y < -0.1) {
                MotionSimulator sim = new MotionSimulator(mc.player);
                sim.simulateWithFriction(2);
                if (currentPlacement.position.getY() > sim.y) {
                    canBuildNow = false;
                }
            }
        }
        if (mc.player.onGround()) {
            canBuildNow = true;
        }
        correctRotation = getPlayerYawRotation();

        if (currentPlacement == null) {
            // 无目标
        } else if (clutch.getValue() && (!canBuildNow || velocityDelay > 0) && rotationDelay <= 8) {
            Rotation rotationToBlock = RotationUtil.rotationToBlock(currentPlacement.position, 1.0f);
            rots.setYawPitch(rotationToBlock.getYaw(), rotationToBlock.getPitch());
            rotationDelay++;
        } else {
            canBuildNow = true;
            rotationDelay = 0;
            if (snap.getValue()) {
                rots.setYaw(correctRotation.getYaw());
            } else {
                rots.setYaw(RotationUtil.moveTowards((float) getBlockDistance(), rots.getYaw(), correctRotation.getYaw()));
            }
            rots.setPitch(correctRotation.getPitch());
            if (sneak.getValue()) {
                eagleTimer++;
                if (eagleTimer == 18) {
                    if (mc.player.isSprinting()) {
                        mc.options.keySprint.setDown(false);
                        mc.player.setSprinting(false);
                    }
                    mc.options.keyShift.setDown(true);
                } else if (eagleTimer >= 21) {
                    mc.options.keyShift.setDown(false);
                    eagleTimer = 0;
                }
            }
            if (eagle.getValue()) {
                mc.options.keyShift.setDown(mc.player.onGround() && isOnBlockEdge(0.3f));
            }
            if (snap.getValue() && !jumpHeld) {
                resetSnap();
            }
        }
        lastRots.setYawPitch(rots.getYaw(), rots.getPitch());

        // 设置目标旋转 + 放置方块
        RotationHandler.isRotating = true;
        RotationHandler.setTargetRotation(rots.clone());
        doSnap();
    }

    private double getBlockDistance() {
        double base = Math.max(60.0, 360.0 / rotationTick.getValue().doubleValue());
        return Math.max(base, 180.0);
    }

    private void doSnap() {
        Minecraft mc = Minecraft.getInstance();
        if (currentPlacement == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        if (!BlockUtil.isPlaceable(mc.player.getMainHandItem())) {
            return;
        }
        Direction facing = currentPlacement.facing;
        if (facing == null) {
            return;
        }
        boolean jumpHeld = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getDefaultKey().getValue());
        if (facing == Direction.UP && !mc.player.onGround() && MovementUtil.isMoving() && !jumpHeld) {
            return;
        }
        if (!shouldBuild()) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(getHitVec(currentPlacement.position, facing), facing, currentPlacement.position, false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result == InteractionResult.SUCCESS) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static boolean isOnBlockEdge(float inflate) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        return !mc.level.getCollisions(mc.player,
                mc.player.getBoundingBox().move(0.0, -0.5, 0.0).inflate(-inflate, 0.0, -inflate))
                .iterator().hasNext();
    }

    public static Vec3 getHitVec(BlockPos pos, Direction direction) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        if (direction != Direction.UP && direction != Direction.DOWN) {
            y += MathUtil.randomDouble(0.3, -0.3);
        } else {
            x += MathUtil.randomDouble(0.3, -0.3);
            z += MathUtil.randomDouble(0.3, -0.3);
        }
        if (direction == Direction.WEST || direction == Direction.EAST) {
            z += MathUtil.randomDouble(0.3, -0.3);
        }
        if (direction == Direction.SOUTH || direction == Direction.NORTH) {
            x += MathUtil.randomDouble(0.3, -0.3);
        }
        return new Vec3(x, y, z);
    }

    private PlacementTarget findPlacementTarget(BlockPos origin) {
        Minecraft mc = Minecraft.getInstance();
        Direction[] directions = {Direction.DOWN, Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.UP};
        PriorityQueue<PlacementCandidate> queue = new PriorityQueue<>(Comparator.comparingDouble(c ->
                Math.abs(c.pos.getX() - origin.getX()) + Math.abs(c.pos.getY() - origin.getY()) + Math.abs(c.pos.getZ() - origin.getZ())));
        HashSet<BlockPos> visited = new HashSet<>();
        queue.offer(new PlacementCandidate(origin, null, 0));
        visited.add(origin);
        double maxDistance = 4.5;
        while (!queue.isEmpty()) {
            PlacementCandidate candidate = queue.poll();
            for (Direction direction : directions) {
                BlockPos neighbor = candidate.pos.relative(direction);
                if (visited.contains(neighbor)) {
                    continue;
                }
                double distance = Math.abs(neighbor.getX() - origin.getX()) + Math.abs(neighbor.getY() - origin.getY()) + Math.abs(neighbor.getZ() - origin.getZ());
                if (distance > maxDistance) {
                    continue;
                }
                visited.add(neighbor);
                if (isValidBlock(neighbor)) {
                    Direction face = direction == Direction.DOWN ? Direction.UP : direction.getOpposite();
                    if (mc.level.getBlockState(neighbor).entityCanStandOnFace(mc.level, neighbor, mc.player, face)) {
                        return new PlacementTarget(neighbor, face);
                    }
                } else if (candidate.depth < 3) {
                    queue.offer(new PlacementCandidate(neighbor, direction, candidate.depth + 1));
                }
            }
        }
        return null;
    }

    private boolean isValidBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (!BlockUtil.isSolid(state) || state.isAir()) {
            return false;
        }
        if (pos.getY() > targetYLevel + 1.0) {
            return false;
        }
        return !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private Rotation getPlayerYawRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || currentPlacement == null) {
            return new Rotation();
        }
        return RotationUtil.rotationToBlock(currentPlacement.position, 0.0f);
    }

    private void applyRotations() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        if (!canBuildNow) {
            eye = mc.player.getEyePosition().add(mc.player.getDeltaMovement().multiply(2.0, 2.0, 2.0));
        }
        if (clutch.getValue() && mc.player.getDeltaMovement().y < 0.01) {
            MotionSimulator sim = new MotionSimulator(mc.player);
            sim.simulateWithFriction(2);
            eye = new Vec3(eye.x, Math.max(sim.y + mc.player.getEyeHeight(), eye.y), eye.z);
        }
        BlockPos belowFeet = BlockPos.containing(eye.x, targetYLevel + 0.1f, eye.z);
        int feetX = belowFeet.getX();
        int feetZ = belowFeet.getZ();
        if (mc.level.getBlockState(belowFeet).entityCanStandOn(mc.level, belowFeet, mc.player)) {
            return;
        }
        if (isAbovePlaceable(eye, belowFeet)) {
            return;
        }
        for (int radius = 1; radius <= 6; radius++) {
            if (isAbovePlaceable(eye, new BlockPos(feetX, targetYLevel - radius, feetZ))) {
                return;
            }
            for (int x = 1; x <= radius; x++) {
                for (int z = 0; z <= radius - x; z++) {
                    int yOff = radius - x - z;
                    for (int signX = 0; signX <= 1; signX++) {
                        for (int signZ = 0; signZ <= 1; signZ++) {
                            BlockPos test = new BlockPos(feetX + (signX == 0 ? x : -x), targetYLevel - yOff, feetZ + (signZ == 0 ? z : -z));
                            if (isAbovePlaceable(eye, test)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isAbovePlaceable(Vec3 from, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        if (!(mc.level.getBlockState(pos).getBlock() instanceof AirBlock)) {
            return false;
        }
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5f, pos.getZ() + 0.5);
        for (Direction direction : Direction.values()) {
            Vec3 offsetCenter = center.add(new Vec3(direction.getNormal().getX() * 0.5, direction.getNormal().getY() * 0.5, direction.getNormal().getZ() * 0.5));
            BlockPos offset = pos.offset(direction.getNormal());
            if (mc.level.getBlockState(offset).entityCanStandOnFace(mc.level, offset, mc.player, direction)) {
                Vec3 delta = offsetCenter.subtract(from);
                if (delta.lengthSqr() <= 20.25 && delta.normalize().dot(Vec3.atLowerCornerOf(direction.getNormal()).normalize()) >= 0.0) {
                    currentPlacement = new PlacementTarget(new BlockPos(offset.getX(), offset.getY(), offset.getZ()), direction.getOpposite());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldBuild() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        return mc.level.isEmptyBlock(below) && BlockUtil.isPlaceable(mc.player.getMainHandItem());
    }

    private void resetSnap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || currentPlacement == null) {
            return;
        }
        boolean lookingAtBlock = false;
        HitResult result = RayTraceUtil.rayTrace(1.0f, rots);
        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            if (blockHit.getBlockPos().equals(currentPlacement.position) && blockHit.getDirection() != Direction.UP) {
                lookingAtBlock = true;
            }
        }
        if (!lookingAtBlock && mc.player.tickCount % 4 == 0) {
            rots.setYaw(mc.player.getYRot() + RandomUtils.nextFloat(0.0f, 0.5f) - 0.25f);
        }
    }

    private record PlacementTarget(BlockPos position, Direction facing) {
    }

    private record PlacementCandidate(BlockPos pos, Direction direction, int depth) {
    }
}
