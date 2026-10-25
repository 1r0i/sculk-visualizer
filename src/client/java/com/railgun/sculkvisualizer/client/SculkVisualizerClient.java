package com.railgun.sculkvisualizer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.railgun.sculkvisualizer.SculkVisualizer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

public class SculkVisualizerClient implements ClientModInitializer {

	// Positions of the sensors whose range display is turned on
	public static final Set<BlockPos> active = new HashSet<>();

	// Display toggles
	public static boolean numbersEnabled = false;
	public static boolean seeThrough = false;

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(SculkVisualizer.id("main"));

	private static KeyMapping clearKey;
	private static KeyMapping numbersKey;
	private static KeyMapping seeThroughKey;
	private static int tickCounter = 0;

	// ---- Actions (shared by the key bindings and the commands) ----

	private static void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal(text));
		}
	}

	public static void clearAll() {
		active.clear();
		say("Sculk Visualizer: all cleared");
	}

	public static void toggleNumbers() {
		numbersEnabled = !numbersEnabled;
		say("Sculk Visualizer numbers: " + (numbersEnabled ? "ON" : "OFF"));
	}

	public static void toggleSeeThrough() {
		seeThrough = !seeThrough;
		say("Sculk Visualizer see-through: " + (seeThrough ? "ON" : "OFF"));
	}

	private static KeyMapping key(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, code, CATEGORY));
	}

	// ---- Initialization ----

	@Override
	public void onInitializeClient() {
		SeeThroughRenderer.register();

		// No default keys; players bind their own in the Controls menu
		clearKey = key("key.sculk-visualizer.clear", InputConstants.UNKNOWN.getValue());
		numbersKey = key("key.sculk-visualizer.numbers", InputConstants.UNKNOWN.getValue());
		seeThroughKey = key("key.sculk-visualizer.seethrough", InputConstants.UNKNOWN.getValue());

		// Commands: /sculkvisualizer clear | numbers | seethrough
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("sculkvisualizer")
					.then(ClientCommands.literal("clear")
							.executes(ctx -> { clearAll(); return 1; }))
					.then(ClientCommands.literal("numbers")
							.executes(ctx -> { toggleNumbers(); return 1; }))
					.then(ClientCommands.literal("seethrough")
							.executes(ctx -> { toggleSeeThrough(); return 1; })));
		});

		// Bare-hand right click: toggle the range display of a single sensor
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide()) return InteractionResult.PASS;               // ignore server-side calls
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS; // bare hand only

			BlockPos pos = hit.getBlockPos();
			var state = level.getBlockState(pos);
			if (!state.is(Blocks.SCULK_SENSOR) && !state.is(Blocks.CALIBRATED_SCULK_SENSOR)) {
				return InteractionResult.PASS;
			}

			if (active.contains(pos)) {
				active.remove(pos);
			} else {
				active.add(pos);
				SensorTracker.scan(Minecraft.getInstance());
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (clearKey.consumeClick()) clearAll();
			while (numbersKey.consumeClick()) toggleNumbers();
			while (seeThroughKey.consumeClick()) toggleSeeThrough();

			// Turn the display off for sensors that were broken
			if (client.level != null && !active.isEmpty()) {
				active.removeIf(pos -> {
					// Keep the entry while its chunk is unloaded (the block just can't be read)
					if (client.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
						return false;
					}
					var st = client.level.getBlockState(pos);
					return !st.is(Blocks.SCULK_SENSOR) && !st.is(Blocks.CALIBRATED_SCULK_SENSOR);
				});
			}
			// Rescan for sensors once per second, only while at least one is active
			if (!active.isEmpty()) {
				tickCounter++;
				if (tickCounter >= 20) {
					tickCounter = 0;
					SensorTracker.scan(client);
				}
			}
		});
	}
}