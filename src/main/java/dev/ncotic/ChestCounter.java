package dev.ncotic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;

public class ChestCounter implements ModInitializer {
	public static final String MOD_ID = "chestcounter";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static boolean isCounting = false;
	public static int startTime = 0;
	public static int length = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");

		CommandRegistrationCallback.EVENT.register(CCCommands::register);
		ServerTickEvents.END_WORLD_TICK.register(ChestCounter::onWorldTick);
	}

	private static void onWorldTick(ServerLevel serverLevel) {
		if (isCounting) {
			if (startTime + length == serverLevel.getServer().getTickCount() || startTime == -1) {
				LOGGER.info("Stopping chest counter!");
				isCounting = false;
				length = serverLevel.getServer().getTickCount() - startTime;
				startTime = 0;

				Component component2 = Component.translatable("chat.square_brackets", Component.translatable("chestcounter.count.stop", length)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
				if (serverLevel.getServer().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
					for (ServerPlayer serverPlayer : serverLevel.getServer().getPlayerList().getPlayers()) {
						if (serverLevel.getServer().getPlayerList().isOp(serverPlayer.getGameProfile())) {
							serverPlayer.sendSystemMessage(component2);
						}
					}
				}

				LOGGER.info("Collecting block entities...");

				HashMap<String, HashMap<String, Integer>> counts = new HashMap<>();

				for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
					player.getChunkTrackingView().forEach((chunkPos) -> {
						LevelChunk chunk = player.level().getChunk(chunkPos.x, chunkPos.z);

						chunk.getBlockEntities().forEach(((blockPos, blockEntity) -> {
							CustomData component = blockEntity.components().get(DataComponents.CUSTOM_DATA);
							if (component == null) return;

							if (!component.contains("ccobject")) return;

							String name = component.copyTag().getString("ccobject");

							CompoundTag tag = blockEntity.saveWithFullMetadata(serverLevel.registryAccess());

							if (!tag.contains("Items")) {
								LOGGER.info("Skipping " + name + " because it has no container data.");
								return;
							}

							HashMap<String, Integer> count = counts.getOrDefault(name, new HashMap<>());

							tag.getList("Items", 10).forEach((itemTag) -> {
								if (itemTag instanceof CompoundTag compoundTag) {
									count.put(compoundTag.getString("id"), count.getOrDefault(compoundTag.getString("id"), 0) + compoundTag.getInt("count"));
									counts.put(name, count);
								}
							});
						}));
					});
				}

				String filename = Date.from(new Date().toInstant()).getTime() + ".json";

				FabricLoader.getInstance().getGameDir().resolve("chestcounter").toFile().mkdirs();
				Path path = FabricLoader.getInstance().getGameDir().resolve("chestcounter/" + filename);

				if (counts.isEmpty()) {
					LOGGER.info("Data is empty, not writing to file.");
					return;
				}

				LOGGER.info("Attempting to write to " + path);

				try {
					FileWriter file = new FileWriter(path.toFile());

					Gson gson = new GsonBuilder().setPrettyPrinting().create();

					JsonObject output = new JsonObject();

					counts.forEach((name, count) -> {
						JsonObject farm = new JsonObject();

						count.forEach(farm::addProperty);

						output.add(name, farm);
					});

					file.write(gson.toJson(output));

					LOGGER.info(gson.toJson(output));

					Component component = Component.translatable("chat.square_brackets", Component.translatable("chestcounter.count.write_file", ".minecraft/chestcounter/" + filename))
							.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
					if (serverLevel.getServer().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
						for (ServerPlayer serverPlayer : serverLevel.getServer().getPlayerList().getPlayers()) {
							if (serverLevel.getServer().getPlayerList().isOp(serverPlayer.getGameProfile())) {
								serverPlayer.sendSystemMessage(component);
							}
						}
					}

					file.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
