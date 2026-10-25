package com.railgun.sculkvisualizer.client.mixin;

import com.railgun.sculkvisualizer.client.VibrationLines;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.VibrationSignalParticle;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(VibrationSignalParticle.Provider.class)
public class VibrationSignalParticleProviderMixin {

    // Runs every time the client creates a vibration particle (origin = x, y, z)
    @Inject(method = "createParticle", at = @At("HEAD"))
    private void sculkvisualizer$onCreate(VibrationParticleOption option, ClientLevel level,
                                          double x, double y, double z,
                                          double xd, double yd, double zd,
                                          RandomSource random,
                                          CallbackInfoReturnable<Particle> cir) {
        Optional<Vec3> destination = option.getDestination().getPosition(level);
        if (destination.isEmpty()) return;
        VibrationLines.add(new Vec3(x, y, z), destination.get(), level.getGameTime());
    }
}