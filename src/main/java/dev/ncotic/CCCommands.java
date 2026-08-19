package dev.ncotic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;

public class CCCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("chestcounter")
                .then(Commands.literal("give")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("block", StringArgumentType.string())
                                        .suggests((commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"chest", "hopper"}, suggestionsBuilder))
                                        .executes(CCCommands::give))))
                .then(Commands.literal("count")
                        .then(Commands.argument("time", TimeArgument.time(1))
                                .suggests((commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"60s", "5m", "1d"}, suggestionsBuilder))
                                .executes(CCCommands::count)))
                .then(Commands.literal("stop")
                        .executes((context1) -> {ChestCounter.startTime = -1; return 1;}))
        );
    }

    public static int give(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().isPlayer()) {
            context.getSource().sendFailure(Component.literal("This command can only be used by players!"));
            return 1;
        }

        String block = context.getArgument("block", String.class);
        ItemStack blockStack;
        switch (block) {
            case "chest":
                blockStack = new ItemStack(Items.CHEST);
                break;
            case "hopper":
                blockStack = new ItemStack(Items.HOPPER);
                break;
            default:
                context.getSource().sendFailure(Component.literal("Invalid block type!"));
                return 1;
        }

        ServerPlayer player = context.getSource().getPlayer();

        ChestCounter.LOGGER.info("Giving {} '{}' to player '{}'", block, context.getArgument("name", String.class), player.getName().getString());
        context.getSource().sendSystemMessage(Component.literal("Gave " + block + " '" + context.getArgument("name", String.class) + "'."));

        blockStack.set(DataComponents.CUSTOM_NAME, Component.literal(context.getArgument("name", String.class)));

        CompoundTag tag = new CompoundTag();
        tag.putString("ccobject", context.getArgument("name", String.class));
        blockStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        player.getInventory().add(blockStack);

        return 0;
    }

    public static int count(CommandContext<CommandSourceStack> context) {
        if (ChestCounter.isCounting) {
            context.getSource().sendFailure(Component.literal("Already counting!"));
            return 1;
        }

        ChestCounter.startTime = context.getSource().getServer().getTickCount();
        ChestCounter.length = context.getArgument("time", Integer.class);
        ChestCounter.isCounting = true;

        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            player.getChunkTrackingView().forEach((chunkPos) -> {
                LevelChunk chunk = player.level().getChunk(chunkPos.x, chunkPos.z);

                chunk.getBlockEntities().forEach(((blockPos, blockEntity) -> {
                    CustomData component = blockEntity.components().get(DataComponents.CUSTOM_DATA);
                    if (component == null) return;

                    if (!component.contains("ccobject")) return;

                    CompoundTag tag = blockEntity.saveWithFullMetadata(player.level().registryAccess());

                    tag.remove("Items");

                    blockEntity.loadWithComponents(tag, player.level().registryAccess());
                }));
            });
        }

        return 0;
    }
}
