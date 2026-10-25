package com.railgun.sculkvisualizer.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class VibrationLines {

    // One received vibration: where it started and which sensor received it
    public record Line(Vec3 from, BlockPos sensor, long expiry) {}

    // How long a line stays visible, in game ticks
    private static final int LIFETIME = 40;

    private static final List<Line> lines = new ArrayList<>();

    // Called from the particle hook with the vibration origin and the particle's destination
    public static void add(Vec3 from, Vec3 destination, long now) {
        for (BlockPos sensor : SculkVisualizerClient.active) {
            // The particle's destination is the center of the receiving sensor
            if (Vec3.atCenterOf(sensor).distanceToSqr(destination) < 0.25) {
                Line line = new Line(from, sensor, now + LIFETIME);
                if (!lines.contains(line)) { // ignore duplicate calls
                    lines.add(line);
                }
                return;
            }
        }
    }

    public static List<Line> get(long now) {
        lines.removeIf(l -> l.expiry() < now);
        return lines;
    }

    public static void clear() {
        lines.clear();
    }
}