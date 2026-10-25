package com.railgun.sculkvisualizer.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SeeThroughRenderer {

    private static final Direction[] DIRS = Direction.values();

    // Radius (in blocks) around the player within which numbers are shown
    private static final int NUM_RADIUS = 4;
    // Maximum number of numbers drawn per frame
    private static final int MAX_NUMBERS = 600;

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(SeeThroughRenderer::extract);
    }

    // True if the cell is not a full block (air, slabs, carpets, grass, water, etc.)
    private static boolean isNotFullBlock(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    private static void extract(LevelExtractionContext context) {
        if (SculkVisualizerClient.active.isEmpty()) {
            ReachCalculator.clearAll();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        boolean onTop = SculkVisualizerClient.seeThrough;
        ReachCalculator.keepOnly(SculkVisualizerClient.active);

        // The block the player is looking at (the one with the outline)
        BlockPos targeted = null;
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            targeted = bhr.getBlockPos();
        }

        BlockPos pp = player.blockPosition();
        int numberCount = 0;
        int sensorIndex = 0;

        for (SensorTracker.Sensor s : SensorTracker.getSensors()) {
            if (!SculkVisualizerClient.active.contains(s.pos())) continue;

            ReachCalculator.Result res = ReachCalculator.get(level, s);

            int rgb = SensorColors.colorFor(s.pos()) & 0x00FFFFFF;
            int outerColor = 0x38000000 | rgb;    // outer (sphere) shell: faint
            int shadowFill = 0x50000000 | rgb;    // faces blocked by wool: faint fill
            int shadowStroke = 0xFF000000 | rgb;  // faces blocked by wool: solid outline

            // ---- Range shell ----
            for (int[] f : res.faces) {
                Vec3 min = new Vec3(f[0], f[1], f[2]);
                Vec3 max = new Vec3(f[0] + 1, f[1] + 1, f[2] + 1);
                boolean shadow = f[4] == 1;

                var g = Gizmos.rect(min, max, DIRS[f[3]],
                        GizmoStyle.fill(shadow ? shadowFill : outerColor));
                if (onTop) g.setAlwaysOnTop();

                if (shadow) {
                    var e = Gizmos.rect(min, max, DIRS[f[3]],
                            GizmoStyle.stroke(shadowStroke));
                    if (onTop) e.setAlwaysOnTop();
                }
            }

            // ---- Sensor block outline ----
            var o = Gizmos.cuboid(new AABB(s.pos()), GizmoStyle.stroke(0xFF000000 | rgb));
            if (onTop) o.setAlwaysOnTop();
            // ---- Lines from each vibration origin to this sensor ----
            // ---- Lines from each vibration origin to this sensor ----
            for (VibrationLines.Line l : VibrationLines.get(level.getGameTime())) {
                if (!l.sensor().equals(s.pos())) continue;

                var ln = Gizmos.line(l.from(), Vec3.atCenterOf(s.pos()), 0xFF000000 | rgb);
                if (onTop) ln.setAlwaysOnTop();

                // Signal strength at the vibration origin (shown regardless of the numbers toggle)
                BlockPos oc = BlockPos.containing(l.from());
                int ox = oc.getX() - s.pos().getX();
                int oy = oc.getY() - s.pos().getY();
                int oz = oc.getZ() - s.pos().getZ();
                int r0 = s.range();
                if (Math.abs(ox) <= r0 && Math.abs(oy) <= r0 && Math.abs(oz) <= r0) {
                    int st = res.strength[res.index(ox, oy, oz)];
                    if (st > 0) {
                        TextGizmo.Style originStyle = TextGizmo.Style
                                .forColorAndCentered(0xFF000000 | rgb)
                                .withScale(TextGizmo.Style.DEFAULT_SCALE * 0.9f);
                        Gizmos.addGizmo(new TextGizmo(l.from().add(0, 0.4, 0),
                                "\u00a7l" + st, originStyle)).setAlwaysOnTop();
                    }
                }
            }

            if (SculkVisualizerClient.numbersEnabled) {
                BlockPos sp = s.pos();
                int range = s.range();
                float lift = sensorIndex * 0.2f; // offsets numbers of different sensors so they don't overlap

                // ---- Targeted block: always shown (always on top, slightly larger) ----
                if (targeted != null) {
                    int rx = targeted.getX() - sp.getX();
                    int ry = targeted.getY() - sp.getY();
                    int rz = targeted.getZ() - sp.getZ();

                    int strength = 0;
                    if (Math.abs(rx) <= range && Math.abs(ry) <= range && Math.abs(rz) <= range) {
                        strength = res.strength[res.index(rx, ry, rz)];
                    }

                    // Nothing is shown outside the detection area (or in cells blocked by wool)
                    if (strength > 0) {
                        TextGizmo.Style targetStyle = TextGizmo.Style
                                .forColorAndCentered(0xFF000000 | rgb)
                                .withScale(TextGizmo.Style.DEFAULT_SCALE * 0.9f);
                        Vec3 pos = new Vec3(targeted.getX() + 0.5, targeted.getY() + 0.5 + lift,
                                targeted.getZ() + 0.5);
                        Gizmos.addGizmo(new TextGizmo(pos, Integer.toString(strength), targetStyle))
                                .setAlwaysOnTop();
                    }
                }

                // ---- Non-full-block cells near the player ----
                TextGizmo.Style style = TextGizmo.Style
                        .forColorAndCentered(0xFF000000 | rgb)
                        .withScale(TextGizmo.Style.DEFAULT_SCALE * 0.6f);

                for (int dx = -NUM_RADIUS; dx <= NUM_RADIUS; dx++) {
                    for (int dy = -NUM_RADIUS; dy <= NUM_RADIUS; dy++) {
                        for (int dz = -NUM_RADIUS; dz <= NUM_RADIUS; dz++) {
                            if (numberCount >= MAX_NUMBERS) break;
                            if (dx * dx + dy * dy + dz * dz > NUM_RADIUS * NUM_RADIUS) continue;

                            int cx = pp.getX() + dx;
                            int cy = pp.getY() + dy;
                            int cz = pp.getZ() + dz;

                            // The targeted block was already handled above
                            if (targeted != null && cx == targeted.getX()
                                    && cy == targeted.getY() && cz == targeted.getZ()) continue;

                            // Coordinates relative to the sensor
                            int rx = cx - sp.getX();
                            int ry = cy - sp.getY();
                            int rz = cz - sp.getZ();
                            if (Math.abs(rx) > range || Math.abs(ry) > range
                                    || Math.abs(rz) > range) continue;

                            int strength = res.strength[res.index(rx, ry, rz)];
                            if (strength == 0) continue; // out of range or blocked by wool

                            BlockPos cell = new BlockPos(cx, cy, cz);
                            // Normally only non-full-block cells; in see-through mode show every reachable cell
                            if (!onTop && !isNotFullBlock(level, cell)) continue;

                            Vec3 pos = new Vec3(cx + 0.5, cy + 0.5 + lift, cz + 0.5);
                            var t = Gizmos.addGizmo(
                                    new TextGizmo(pos, Integer.toString(strength), style));
                            if (onTop) t.setAlwaysOnTop();
                            numberCount++;
                        }
                    }
                }
            }
            sensorIndex++;
        }
    }
}