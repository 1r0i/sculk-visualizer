package com.railgun.sculkvisualizer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public class SensorTracker {

    // One sensor: its position and detection range (normal 8, calibrated 16)
    public record Sensor(BlockPos pos, int range) {}

    // Sensors found by the last scan
    private static List<Sensor> sensors = List.of();

    // How many chunks around the player to scan (1 chunk = 16 blocks)
    private static final int CHUNK_RADIUS = 6;

    public static List<Sensor> getSensors() {
        return sensors;
    }

    public static void scan(Minecraft client) {
        if (client.level == null || client.player == null) {
            sensors = List.of();
            return;
        }

        BlockPos playerPos = client.player.blockPosition();
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        List<Sensor> found = new ArrayList<>();

        for (int cx = centerChunkX - CHUNK_RADIUS; cx <= centerChunkX + CHUNK_RADIUS; cx++) {
            for (int cz = centerChunkZ - CHUNK_RADIUS; cz <= centerChunkZ + CHUNK_RADIUS; cz++) {
                LevelChunk chunk = client.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue; // chunk not loaded yet

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SculkSensorBlockEntity) {
                        boolean calibrated = be.getBlockState().is(Blocks.CALIBRATED_SCULK_SENSOR);
                        found.add(new Sensor(be.getBlockPos(), calibrated ? 16 : 8));
                    }
                }
            }
        }
        sensors = found;
    }
}