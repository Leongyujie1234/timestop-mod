package com.timestop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.ChatFormatting;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Main entry point for the TimeStop Fabric mod.
 *
 * <p>This mod provides a server-side stun system that freezes players in place
 * by silently dropping their movement packets. Unlike the previous approach
 * that registered a custom {@link net.minecraft.world.effect.MobEffect}, this
 * version uses a lightweight in-memory map ({@link StunManager}) to track
 * stunned players. This avoids Fabric's registry sync sending unknown entries
 * to vanilla clients, which caused "Received a registry entry that is unknown
 * to this client" disconnects.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code /stun <player>} — stun a player indefinitely</li>
 *   <li>{@code /stun <player> <seconds>} — stun a player for a duration</li>
 *   <li>{@code /unstun <player>} — remove the stun</li>
 *   <li>{@code /stun list} — list all currently stunned players</li>
 * </ul>
 *
 * <p>Compatible with vanilla clients joining a Fabric server.
 */
public class TimeStopMod implements DedicatedServerModInitializer {

    /** Mod ID used as the namespace for all registered assets. */
    public static final String MOD_ID = "timestop";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        LOGGER.info("TimeStop mod initialized — using StunManager (no registry entries, vanilla-client compatible)");
    }

    /**
     * Registers the {@code /stun} and {@code /unstun} commands.
     */
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /stun <player> [duration]
        dispatcher.register(Commands.literal("stun")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> stunPlayer(
                                ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player"),
                                0 // infinite
                        ))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                .executes(ctx -> stunPlayer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "duration")
                                ))
                        )
                )
                .then(Commands.literal("list")
                        .executes(ctx -> listStunned(ctx.getSource()))
                )
        );

        // /unstun <player>
        dispatcher.register(Commands.literal("unstun")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> unstunPlayer(
                                ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")
                        ))
                )
        );
    }

    private static int stunPlayer(CommandSourceStack source, ServerPlayer target, int durationSeconds) {
        long currentTick = source.getServer().getTickCount();
        StunManager.stunForSeconds(target.getUUID(), currentTick, durationSeconds);

        String durationText = durationSeconds <= 0 ? "indefinitely" : durationSeconds + "s";
        source.sendSuccess(() -> Component.literal("[TimeStop] ")
                .append(target.getName())
                .append(Component.literal(" stunned " + durationText))
                .withStyle(ChatFormatting.YELLOW), true);

        LOGGER.info("Stunned {} for {}", target.getName().getString(), durationText);
        return 1;
    }

    private static int unstunPlayer(CommandSourceStack source, ServerPlayer target) {
        boolean wasStunned = StunManager.unstun(target.getUUID());

        if (wasStunned) {
            source.sendSuccess(() -> Component.literal("[TimeStop] ")
                    .append(target.getName())
                    .append(Component.literal(" is no longer stunned"))
                    .withStyle(ChatFormatting.GREEN), true);
            LOGGER.info("Unstunned {}", target.getName().getString());
        } else {
            source.sendFailure(Component.literal("[TimeStop] ")
                    .append(target.getName())
                    .append(Component.literal(" is not stunned"))
                    .withStyle(ChatFormatting.RED));
        }
        return wasStunned ? 1 : 0;
    }

    private static int listStunned(CommandSourceStack source) {
        var stunned = StunManager.getStunnedPlayers();
        var server = source.getServer();

        if (stunned.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[TimeStop] No players are currently stunned")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        MutableComponent list = Component.literal("[TimeStop] Stunned players: ").withStyle(ChatFormatting.YELLOW);
        boolean first = true;
        for (UUID uuid : stunned) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                if (!first) list.append(Component.literal(", "));
                list.append(player.getName());
                first = false;
            }
        }

        final MutableComponent finalList = list;
        source.sendSuccess(() -> finalList, false);
        return stunned.size();
    }
}
