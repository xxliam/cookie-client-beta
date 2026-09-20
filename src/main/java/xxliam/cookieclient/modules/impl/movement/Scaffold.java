package xxliam.cookieclient.modules.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.BooleanSetting;
import xxliam.cookieclient.settings.impl.ModeSetting;
import xxliam.cookieclient.utils.game.BlockUtil;
import xxliam.cookieclient.utils.game.RayTraceUtil;
import xxliam.cookieclient.utils.math.MathUtil;
import xxliam.cookieclient.utils.rotation.Rotation;
import xxliam.cookieclient.utils.rotation.RotationHandler;
import xxliam.cookieclient.utils.rotation.RotationUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Scaffold：自动搭路 —— LiquidBounce {@code ModuleScaffold} + {@code ScaffoldNormalTechnique} 移植。
 * <p>
 * 核心管线（照搬 LB Normal）：
 * <ol>
 *   <li>预测位置 = 当前位置 + 当前速度（LB {@code ScaffoldMovementPrediction} 的单 tick 简化）；</li>
 *   <li>目标搜索：对 {@code BlockPosOffsets.NORMAL} 偏移表（xz ∈ {0,±1} × y ∈ {0,-1}，按离预测
 *       位置平方距离升序）逐格考察 —— 空格/流体 → 点击相邻实心块的面（PLACE_AT_NEIGHBOR）；
 *       可替换方块（雪层等）→ 直接往里放（REPLACE）；按「面中心与当前旋转的角差」选最优面，
 *       并剔除背对玩家的面（LB 的 cosine &gt; 0 判定）；</li>
 *   <li>面向点：碰撞箱轴对齐面 → 上部面截到 y≤0.6（LB 方便从整块切半砖）→
 *       {@code trimFace}（四边各收 15%）→ 按 Rotation Mode 产出点 → 面底缘记为
 *       {@code minPlacementY}（十字准星命中点不得低于它）；</li>
 *   <li>旋转 NORMAL 时机：找到目标即 {@code RotationHandler.setTargetRotation}（由
 *       LocalPlayerMixin 在发包时应用）；</li>
 *   <li>放置：用当前旋转做十字准星射线，命中块 / 面 / 点高度三重匹配后才 {@code useItemOn}
 *       （对应 LB {@code doesCrosshairTargetMatchRequirements}），成功后按 Swing 模式挥手。</li>
 * </ol>
 * <p>
 * Swing 四档逐字照搬 LB {@code SwingMode}：
 * <pre>
 *   DoNotHide     player.swing(hand)                 —— 客户端动画 + 服务端包（原版行为）
 *   HideForBoth   无                                 —— 双端都不挥
 *   HideForClient 仅发 ServerboundSwingPacket         —— 服务端挥、客户端无动画
 *   HideForServer player.swing(hand, false)          —— 客户端动画、不发服务端包
 * </pre>
 * <p>
 * 与 LB 的差异（刻意裁剪 / 语义等价，均已确认）：①未移植 Telly/Eagle/Down/Ceiling/
 * HeadHitter/Tower/SameY —— 本轮只要求 Normal + Swing；②Rotation Mode 只移植
 * Center / Random / Stabilized / NearestRotation 四档（LB 另有 ReverseYaw / DiagonalYaw /
 * AngleYaw / EdgePoint，依赖 LB 的平面-线段求交几何库）；③Stabilized 的面向区域裁剪用
 * 「玩家位置 + 水平速度方向」直线等价替代 LB 的移动规划器最优线；④移动预测为单 tick 速度外推；
 * ⑤LB「面到旋转线的最近点」按「射线与面所在平面求交 + 钳制到面矩形」实现（线与平面相交时二者等价）。
 */
public class Scaffold extends Module {

    public static Scaffold INSTANCE;

    public final ModeSetting rotationMode = new ModeSetting("Rotation Mode",
            "Stabilized", "Center", "Random", "NearestRotation").withDefault("Stabilized");
    public final BooleanSetting requiresSight = new BooleanSetting("Requires Sight", false);
    public final ModeSetting swingMode = new ModeSetting("Swing",
            "DoNotHide", "HideForBoth", "HideForClient", "HideForServer").withDefault("DoNotHide");

    private static final RandomGenerator RANDOM = new java.util.Random();

    private int oldSlot;
    private Target currentTarget;

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(rotationMode);
        addSetting(requiresSight);
        addSetting(swingMode);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            oldSlot = mc.player.getInventory().selected;
        }
        currentTarget = null;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.getInventory().selected = oldSlot;
        }
        currentTarget = null;
        RotationHandler.isRotating = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (mc.player.isUsingItem()) {
            return;
        }

        autoSwitchToBlock(mc);

        // LB 用移动预测器，这里单 tick 速度外推
        Vec3 predictedPos = mc.player.position().add(mc.player.getDeltaMovement());
        currentTarget = findPlacementTarget(predictedPos);

        if (currentTarget != null) {
            RotationHandler.isRotating = true;
            RotationHandler.setTargetRotation(currentTarget.rotation());
        } else {
            RotationHandler.isRotating = false;
        }

        placeIfCrosshairValid(mc);
    }

    /** 找到快捷栏第一个可用方块堆就切过去（LB 由 AutoBlock 静默选块，这里沿用旧实现的直接切换）。 */
    private void autoSwitchToBlock(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                if (mc.player.getInventory().selected != i) {
                    mc.player.getInventory().selected = i;
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // 目标搜索（LB TargetFinding.findBestBlockPlacementTarget 的 NORMAL 偏移路径）
    // ------------------------------------------------------------------

    private Target findPlacementTarget(Vec3 predictedPos) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eyePos = mc.player.getEyePosition();
        // LB getTargetedPosition：SameY=Off 默认 → 站位方块向下一格
        BlockPos base = BlockPos.containing(predictedPos.x, predictedPos.y, predictedPos.z).below();

        for (BlockPos targetPos : normalOffsetsSorted(base, predictedPos)) {
            BlockState targetState = mc.level.getBlockState(targetPos);
            if (isSolidForPlacement(targetState, targetPos)) {
                continue;
            }
            boolean placeAtNeighbor = targetState.isAir() || !targetState.getFluidState().isEmpty();
            if (!placeAtNeighbor && !targetState.canBeReplaced()) {
                continue;
            }

            Direction bestDirection = null;
            double bestDelta = Double.MAX_VALUE;
            BlockPos bestNeighbor = null;
            for (Direction direction : Direction.values()) {
                // PLACE_AT_NEIGHBOR：点击目标格旁的实心块；REPLACE：直接点可替换块自身
                BlockPos neighbor = placeAtNeighbor
                        ? targetPos.relative(direction.getOpposite())
                        : targetPos;
                BlockState neighborState = mc.level.getBlockState(neighbor);
                if (neighborState.canBeReplaced()) {
                    continue; // 邻块可被替换（草/花）→ 点它放不出方块
                }
                Vec3 sideCenter = Vec3.atCenterOf(neighbor).add(
                        direction.getStepX() * 0.5, direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
                // 背面剔除（LB calculateAngleToPlayerEyeCosine < 0 剔除）
                Vec3 eyeToFace = eyePos.subtract(sideCenter);
                if (eyeToFace.lengthSqr() < 1.0e-8) {
                    continue;
                }
                double cosine = eyeToFace.normalize().dot(
                        new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ()));
                if (cosine <= 0.0) {
                    continue;
                }
                double delta = rotationDelta(RotationUtil.rotationTo(eyePos, sideCenter));
                if (delta < bestDelta) {
                    bestDelta = delta;
                    bestDirection = direction;
                    bestNeighbor = neighbor;
                }
            }
            if (bestDirection == null) {
                continue;
            }

            Target target = buildTarget(bestNeighbor, targetPos, bestDirection, eyePos);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    /** BlockPosOffsets.NORMAL：xz ∈ {0,-1,1} × y ∈ {0,-1}，按「块中心到预测位置」平方距离升序。 */
    private List<BlockPos> normalOffsetsSorted(BlockPos base, Vec3 predictedPos) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> offsets = new ArrayList<>(18);
        for (int x : new int[]{0, -1, 1}) {
            for (int z : new int[]{0, -1, 1}) {
                for (int y : new int[]{0, -1}) {
                    BlockPos pos = base.offset(x, y, z);
                    if (mc.level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    offsets.add(pos);
                }
            }
        }
        offsets.sort(Comparator.comparingDouble(pos ->
                Vec3.atCenterOf(pos).distanceToSqr(predictedPos)));
        return offsets;
    }

    /** 在选中面上找放置点并生成最终旋转（LB findTargetPointOnFace + BlockPlacementTarget）。 */
    private Target buildTarget(BlockPos neighbor, BlockPos targetPos, Direction direction, Vec3 eyePos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState neighborState = mc.level.getBlockState(neighbor);
        List<AABB> shapes = neighborState.getShape(mc.level, neighbor, CollisionContext.of(mc.player)).toAabbs();
        if (shapes.isEmpty()) {
            return null;
        }

        Target best = null;
        double bestCenteredness = Double.MAX_VALUE;
        double bestFromY = -Double.MAX_VALUE;
        for (AABB shape : shapes) {
            Face face = alignedFace(shape, direction);
            Face working = face;
            // LB：上半部分的面截到 y≤0.6，方便从整块切到半砖/台阶
            if (working.localMaxY() >= 0.9) {
                Face truncated = working.truncateY(0.6);
                if (truncated != null) {
                    working = truncated;
                }
            }
            working = working.trimmed();
            Vec3 interactionPoint = producePositionOnFace(working, direction, neighbor);
            if (interactionPoint == null) {
                continue;
            }
            // LB COMPARATOR_POINT_ON_FACE：点越贴面心越好（沿法线轴度量），其次点越高
            Vec3 centered = interactionPoint.subtract(Vec3.atCenterOf(neighbor))
                    .multiply(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            double centeredness = centered.lengthSqr();
            if (best == null || centeredness < bestCenteredness
                    || (centeredness == bestCenteredness && face.localMinY() > bestFromY)) {
                bestCenteredness = centeredness;
                bestFromY = face.localMinY();
                Rotation rotation = RotationUtil.rotationTo(eyePos, interactionPoint);
                best = new Target(neighbor, targetPos, direction, interactionPoint,
                        face.localMinY() + neighbor.getY(), rotation);
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 面向点工厂（LB FaceTargetPositionFactory）
    // ------------------------------------------------------------------

    private Vec3 producePositionOnFace(Face face, Direction direction, BlockPos targetPos) {
        return switch (rotationMode.getValue()) {
            case "Center" -> face.worldCenter(targetPos);
            case "Random" -> face.worldRandomPoint(targetPos);
            case "NearestRotation" -> nearestPointToRotationLine(face, targetPos);
            default -> produceStabilized(face, direction, targetPos); // Stabilized（LB 默认）
        };
    }

    /**
     * Stabilized：先用移动方向裁剪面向区域（LB 用移动规划器最优线，这里用「玩家位置 +
     * 水平速度方向」直线等价），再取当前旋转射线与面的交点。
     */
    private Vec3 produceStabilized(Face face, Direction direction, BlockPos targetPos) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 velocity = mc.player.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        Face working = face;
        if (horizontal.lengthSqr() > 0.001 * 0.001) {
            Vec3 dir = horizontal.normalize();
            double planeFixed = face.worldFixed(targetPos);
            Double t = rayPlaneT(mc.player.position(), dir, planeFixed, face.axis());
            if (t != null) {
                Vec3 intersect = mc.player.position().add(dir.scale(t));
                Vec3 ahead = mc.player.position().add(dir.scale(2.0));
                AABB cropBox = new AABB(
                        Math.min(intersect.x, ahead.x), mc.player.getY() - 2.0, Math.min(intersect.z, ahead.z),
                        Math.max(intersect.x, ahead.x), mc.player.getY() + 1.0, Math.max(intersect.z, ahead.z));
                Face clamped = face.clampToWorldBox(cropBox, targetPos);
                // LB：裁完剩太少就不采样，直接用未裁剪面
                if (clamped != null && clamped.area() >= 0.0001) {
                    working = clamped;
                }
            }
        }
        return nearestPointToRotationLine(working, targetPos);
    }

    /**
     * 当前旋转射线与面的交点（钳制到面矩形内）。
     * LB {@code face.nearestPointTo(rotationLine)} 的语义等价：射线与面平面相交时二者一致。
     */
    private Vec3 nearestPointToRotationLine(Face face, BlockPos targetPos) {
        Rotation baseline = currentRotationBaseline();
        Vec3 dir = RayTraceUtil.getViewVector(baseline.getPitch(), baseline.getYaw());
        Vec3 eye = Minecraft.getInstance().player.getEyePosition();
        Double t = rayPlaneT(eye, dir, face.worldFixed(targetPos), face.axis());
        Vec3 point = t == null ? face.worldCenter(targetPos) : eye.add(dir.scale(t));
        return new Vec3(
                MathUtil.clamp(point.x, face.worldMinX(targetPos), face.worldMaxX(targetPos)),
                MathUtil.clamp(point.y, face.worldMinY(targetPos), face.worldMaxY(targetPos)),
                MathUtil.clamp(point.z, face.worldMinZ(targetPos), face.worldMaxZ(targetPos)));
    }

    private Rotation currentRotationBaseline() {
        if (RotationHandler.isRotating && RotationHandler.targetRotation != null) {
            return RotationHandler.targetRotation;
        }
        Minecraft mc = Minecraft.getInstance();
        return new Rotation(mc.player.getYRot(), mc.player.getXRot());
    }

    private double rotationDelta(Rotation target) {
        Rotation current = currentRotationBaseline();
        double dy = RotationUtil.angleDiffDouble(current.getYaw(), target.getYaw());
        double dp = current.getPitch() - target.getPitch();
        return Math.sqrt(dy * dy + (double) dp * dp);
    }

    // ------------------------------------------------------------------
    // 面几何（LB AlignedFace 的轴对齐简化）
    // ------------------------------------------------------------------

    /** 抽出碰撞箱在 direction 侧的轴对齐面矩形（块局部坐标）。 */
    private Face alignedFace(AABB shape, Direction direction) {
        return switch (direction) {
            case DOWN -> new Face(shape.minX, shape.minZ, shape.maxX, shape.maxZ, Axis.Y, shape.minY);
            case UP -> new Face(shape.minX, shape.minZ, shape.maxX, shape.maxZ, Axis.Y, shape.maxY);
            case NORTH -> new Face(shape.minX, shape.minY, shape.maxX, shape.maxY, Axis.Z, shape.minZ);
            case SOUTH -> new Face(shape.minX, shape.minY, shape.maxX, shape.maxY, Axis.Z, shape.maxZ);
            case WEST -> new Face(shape.minY, shape.minZ, shape.maxY, shape.maxZ, Axis.X, shape.minX);
            case EAST -> new Face(shape.minY, shape.minZ, shape.maxY, shape.maxZ, Axis.X, shape.maxX);
        };
    }

    private enum Axis {X, Y, Z}

    /**
     * 轴对齐面矩形（块局部坐标）。u/v = 面内两个自由轴的坐标范围，
     * fixedAxis/fixedValue = 法线轴固定坐标。X 面 → u=Y、v=Z；Y 面 → u=X、v=Z；Z 面 → u=X、v=Y。
     * worldMinX..worldMaxZ 系列按 X/Y/Z 世界轴给出（含方块坐标偏移）。
     */
    private record Face(double uFrom, double vFrom, double uTo, double vTo, Axis axis, double fixedValue) {

        double worldMinX(BlockPos pos) {
            return pos.getX() + (axis == Axis.X ? fixedValue : uFrom);
        }

        double worldMaxX(BlockPos pos) {
            return pos.getX() + (axis == Axis.X ? fixedValue : uTo);
        }

        double worldMinY(BlockPos pos) {
            return pos.getY() + (axis == Axis.Y ? fixedValue : (axis == Axis.X ? uFrom : vFrom));
        }

        double worldMaxY(BlockPos pos) {
            return pos.getY() + (axis == Axis.Y ? fixedValue : (axis == Axis.X ? uTo : vTo));
        }

        double worldMinZ(BlockPos pos) {
            return pos.getZ() + (axis == Axis.Z ? fixedValue : vFrom);
        }

        double worldMaxZ(BlockPos pos) {
            return pos.getZ() + (axis == Axis.Z ? fixedValue : vTo);
        }

        double worldFixed(BlockPos pos) {
            return switch (axis) {
                case X -> pos.getX() + fixedValue;
                case Y -> pos.getY() + fixedValue;
                case Z -> pos.getZ() + fixedValue;
            };
        }

        double area() {
            return (uTo - uFrom) * (vTo - vFrom);
        }

        /** 块局部 Y 下缘（法线为 Y 轴的面 = fixedValue）。 */
        double localMinY() {
            return axis == Axis.Y ? fixedValue : (axis == Axis.X ? uFrom : vFrom);
        }

        /** 块局部 Y 上缘。 */
        double localMaxY() {
            return axis == Axis.Y ? fixedValue : (axis == Axis.X ? uTo : vTo);
        }

        Vec3 worldCenter(BlockPos pos) {
            return new Vec3(
                    (worldMinX(pos) + worldMaxX(pos)) / 2.0,
                    (worldMinY(pos) + worldMaxY(pos)) / 2.0,
                    (worldMinZ(pos) + worldMaxZ(pos)) / 2.0);
        }

        Vec3 worldRandomPoint(BlockPos pos) {
            // nextDouble(origin, bound) 要求 origin < bound，固定轴（min==max）直接取值
            return new Vec3(
                    randRange(worldMinX(pos), worldMaxX(pos)),
                    randRange(worldMinY(pos), worldMaxY(pos)),
                    randRange(worldMinZ(pos), worldMaxZ(pos)));
        }

        private double randRange(double from, double to) {
            return to > from ? RANDOM.nextDouble(from, to) : from;
        }

        /** LB truncateY(0.6)：把面上部截到 y=0.6；法线为 Y 轴的面或截空返回 null。 */
        Face truncateY(double limit) {
            if (axis == Axis.Y) {
                return null;
            }
            double newMax = axis == Axis.X ? Math.min(uTo, limit) : Math.min(vTo, limit);
            if (axis == Axis.X) {
                return newMax > uFrom ? new Face(uFrom, vFrom, newMax, vTo, axis, fixedValue) : null;
            }
            return newMax > vFrom ? new Face(uFrom, vFrom, uTo, newMax, axis, fixedValue) : null;
        }

        /** LB trimFace：四边各收 15% 尺寸，收空则取中心线。 */
        Face trimmed() {
            double offU = (uTo - uFrom) * 0.15;
            double offV = (vTo - vFrom) * 0.15;
            double u1 = uFrom + offU;
            double u2 = uTo - offU;
            double v1 = vFrom + offV;
            double v2 = vTo - offV;
            if (u1 > u2) {
                u1 = u2 = (uFrom + uTo) / 2.0;
            }
            if (v1 > v2) {
                v1 = v2 = (vFrom + vTo) / 2.0;
            }
            return new Face(u1, v1, u2, v2, axis, fixedValue);
        }

        /** 与世界坐标 AABB 求交；交不出面积返回 null。 */
        Face clampToWorldBox(AABB box, BlockPos pos) {
            double x1 = Math.max(worldMinX(pos), box.minX);
            double x2 = Math.min(worldMaxX(pos), box.maxX);
            double y1 = Math.max(worldMinY(pos), box.minY);
            double y2 = Math.min(worldMaxY(pos), box.maxY);
            double z1 = Math.max(worldMinZ(pos), box.minZ);
            double z2 = Math.min(worldMaxZ(pos), box.maxZ);
            if (x1 > x2 || y1 > y2 || z1 > z2) {
                return null;
            }
            return switch (axis) {
                case X -> new Face(y1 - pos.getY(), z1 - pos.getZ(), y2 - pos.getY(), z2 - pos.getZ(), axis, fixedValue);
                case Y -> new Face(x1 - pos.getX(), z1 - pos.getZ(), x2 - pos.getX(), z2 - pos.getZ(), axis, fixedValue);
                case Z -> new Face(x1 - pos.getX(), y1 - pos.getY(), x2 - pos.getX(), y2 - pos.getY(), axis, fixedValue);
            };
        }
    }

    /** 直线 (from, dir) 与面所在平面（法线轴 axis）的交点参数 t；近平行返回 null。 */
    private Double rayPlaneT(Vec3 from, Vec3 dir, double planeFixed, Axis axis) {
        double d = switch (axis) {
            case X -> dir.x;
            case Y -> dir.y;
            case Z -> dir.z;
        };
        if (Math.abs(d) < 1.0e-6) {
            return null;
        }
        double f = switch (axis) {
            case X -> from.x;
            case Y -> from.y;
            case Z -> from.z;
        };
        return (planeFixed - f) / d;
    }

    // ------------------------------------------------------------------
    // 放置与挥手
    // ------------------------------------------------------------------

    private void placeIfCrosshairValid(Minecraft mc) {
        Target target = currentTarget;
        if (target == null) {
            return;
        }
        // RequiresSight：用目标旋转做额外可见性校验（LB RequiresSight）
        if (requiresSight.getValue()
                && !RayTraceUtil.canRayTrace(target.rotation(), target.direction(), target.interactedBlockPos(), true)) {
            return;
        }

        Rotation current = currentRotationBaseline();
        HitResult hit = RayTraceUtil.rayTrace(1.0f, current);
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        // LB doesCrosshairTargetMatchRequirements：块 / 面 / 点高度三重匹配
        if (!blockHit.getBlockPos().equals(target.interactedBlockPos())
                || blockHit.getDirection() != target.direction()
                || blockHit.getLocation().y < target.minPlacementY()) {
            return;
        }

        InteractionHand hand = BlockUtil.isPlaceable(mc.player.getMainHandItem())
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!BlockUtil.isPlaceable(mc.player.getItemInHand(hand))) {
            return;
        }

        BlockHitResult placeHit = new BlockHitResult(target.interactionPoint(), target.direction(),
                target.interactedBlockPos(), false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, placeHit);
        if (result.consumesAction()) {
            swing(hand);
            currentTarget = null;
        }
    }

    /** LB SwingMode.accept 逐字语义。 */
    private void swing(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        switch (swingMode.getValue()) {
            case "DoNotHide" -> mc.player.swing(hand);
            case "HideForBoth" -> {
                // 双端都不挥
            }
            case "HideForClient" -> mc.player.connection.send(new ServerboundSwingPacket(hand));
            case "HideForServer" -> mc.player.swing(hand, false);
            default -> mc.player.swing(hand);
        }
    }

    private boolean isSolidForPlacement(BlockState state, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return !state.getCollisionShape(mc.level, pos).isEmpty() && !state.canBeReplaced();
    }

    /** 一次放置目标：interactedBlockPos = 被点击的块，placedBlockPos = 方块将出现的格。 */
    private record Target(BlockPos interactedBlockPos, BlockPos placedBlockPos, Direction direction,
                          Vec3 interactionPoint, double minPlacementY, Rotation rotation) {
    }
}
