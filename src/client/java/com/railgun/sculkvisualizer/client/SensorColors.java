package com.railgun.sculkvisualizer.client;

import net.minecraft.core.BlockPos;

public class SensorColors {

    // Picks a color from the sensor position (the same sensor always gets the same color), translucent ARGB
    static int colorFor(BlockPos pos) {
        int hash = pos.hashCode() * 31;
        float hue = ((hash & 0xFFFF) % 360) / 360f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.9f, 1.0f) & 0x00FFFFFF;
        return 0xCC000000 | rgb;
    }
}