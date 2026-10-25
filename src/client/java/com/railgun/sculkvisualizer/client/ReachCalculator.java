package com.railgun.sculkvisualizer.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReachCalculator {

    public static class Result {
        public final BlockPos sensor;
        public final int range;
        public final int n;                 // cells per side (2 * range + 1)
        public final byte[] strength;       // signal strength per cell (0 = unreachable)
        // Shell faces as {x, y, z, direction ordinal, 1 = shadow face / 0 = outer face}
        public final List<int[]> faces = new ArrayList<>();
        long signature;
        long lastCheck;

        Result(BlockPos sensor, int range) {
            this.sensor = sensor;
            this.range = range;
            this.n = 2 * range + 1;
            this.strength = new byte[n * n * n];
        }

        public int index(int dx, int dy, int dz) {
            return ((dx + range) * n + (dy + range)) * n + (dz + range);
        }
    }

    private static final Map<BlockPos, Result> cache = new HashMap<>();
    private static final long RECHECK_TICKS = 1;

    // Fingerprint value when there are no vibration-occluding blocks nearby
    private static final long EMPTY_SIGNATURE = 1125899906842597L;

    public static void clearAll() {
        cache.clear();
    }

    public static void keepOnly(java.util.Set<BlockPos> keep) {
        cache.keySet().retainAll(keep);
    }

    public static Result get(ClientLevel level, SensorTracker.Sensor s) {
        long now = level.getGameTime();
        Result r = cache.get(s.pos());
        if (r == null) {
            r = compute(level, s, signature(level, s));
            r.lastCheck = now;
            cache.put(s.pos(), r);
        } else if (now - r.lastCheck >= RECHECK_TICKS) {
            r.lastCheck = now;
            long sig = signature(level, s);
            if (sig != r.signature) {
                r = compute(level, s, sig);
                r.lastCheck = now;
                cache.put(s.pos(), r);
            }
        }
        return r;
    }

    // A fingerprint of the vibration-occluding blocks around the sensor, used to detect changes
    private static long signature(ClientLevel level, SensorTracker.Sensor s) {
        long h = EMPTY_SIGNATURE;
        int r = s.range();
        BlockPos sp = s.pos();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(sp.getX() + dx, sp.getY() + dy, sp.getZ() + dz);
                    if (level.getBlockState(m).is(BlockTags.OCCLUDES_VIBRATION_SIGNALS)) {
                        h = 31 * h + m.asLong();
                    }
                }
            }
        }
        return h;
    }

    private static Result compute(ClientLevel level, SensorTracker.Sensor s, long signature) {
        int r = s.range();
        BlockPos sp = s.pos();
        Result res = new Result(sp, r);
        res.signature = signature;
        Vec3 sensorCenter = Vec3.atCenterOf(sp);

        // 1) For every cell, work out whether it is reachable and its signal strength
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r * r) continue; // outside the range (sphere)

                    Vec3 cell = new Vec3(sp.getX() + dx + 0.5, sp.getY() + dy + 0.5, sp.getZ() + dz + 0.5);
                    if (d2 > 0 && res.signature != EMPTY_SIGNATURE
                            && isOccluded(level, cell, sensorCenter)) continue; // blocked by wool etc.

                    float dist = (float) Math.sqrt(d2);
                    res.strength[res.index(dx, dy, dz)] =
                            (byte) VibrationSystem.getRedstoneStrengthForDistance(dist, r);
                }
            }
        }

        // 2) Find the faces that form the outer shell of the reachable region
        Direction[] dirs = Direction.values();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (res.strength[res.index(dx, dy, dz)] == 0) continue;
                    for (Direction d : dirs) {
                        int nx = dx + d.getStepX(), ny = dy + d.getStepY(), nz = dz + d.getStepZ();
                        boolean neighborReachable = false;
                        boolean neighborInSphere = false;
                        if (Math.abs(nx) <= r && Math.abs(ny) <= r && Math.abs(nz) <= r) {
                            neighborReachable = res.strength[res.index(nx, ny, nz)] != 0;
                            neighborInSphere = nx * nx + ny * ny + nz * nz <= r * r;
                        }
                        if (!neighborReachable) {
                            // Neighbor is inside the sphere but unreachable: a "shadow" face caused by occlusion
                            res.faces.add(new int[]{sp.getX() + dx, sp.getY() + dy, sp.getZ() + dz,
                                    d.ordinal(), neighborInSphere ? 1 : 0});
                        }
                    }
                }
            }
        }
        return res;
    }

    // Vanilla-style occlusion test: cast rays from the origin, slightly offset in each of the
    // 6 directions; the vibration is blocked only if ALL of them are blocked
    private static boolean isOccluded(ClientLevel level, Vec3 origin, Vec3 dest) {
        Vec3 a = new Vec3(Mth.floor(origin.x) + 0.5, Mth.floor(origin.y) + 0.5, Mth.floor(origin.z) + 0.5);
        Vec3 b = new Vec3(Mth.floor(dest.x) + 0.5, Mth.floor(dest.y) + 0.5, Mth.floor(dest.z) + 0.5);
        for (Direction dir : Direction.values()) {
            Vec3 start = a.relative(dir, 1.0E-5F);
            if (level.isBlockInLine(new ClipBlockStateContext(start, b,
                    state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))).getType() != HitResult.Type.BLOCK) {
                return false;
            }
        }
        return true;
    }
}