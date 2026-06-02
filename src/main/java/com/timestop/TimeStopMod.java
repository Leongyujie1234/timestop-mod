package com.timestop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * Main entry point for the TimeStop Fabric mod.
 *
 * <p>This mod provides a server-side stun system that fully freezes players:
 * <ul>
 *   <li>Movement packets are cancelled server-side</li>
 *   <li>Every tick, stunned players are force-teleported back to their
 *       capture position so their client snaps back immediately</li>
 *   <li>Interaction packets (block place/break, item use, entity interact)
 *       are cancelled server-side, and inventory is synced back to prevent
 *       ghost items on the client</li>
 * </ul>
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
        // Register /stun and /unstun commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        // Every tick: force-sync stunned players back to their capture position
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long currentTick = server.getTickCount();

            for (UUID uuid : StunManager.getStunnedPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    // Player disconnected while stunned — clean up
                    StunManager.unstun(uuid);
                    continue;
                }

                StunManager.StunData data = StunManager.getStunData(uuid);

                // Check if stun has expired
                if (data != null && currentTick > data.expiryTick) {
                    StunManager.unstun(uuid);
                    LOGGER.info("Stun expired for {}", player.getName().getString());
                    continue;
                }

                // Force-teleport the player back to their capture position.
                // This sends a ClientboundPlayerPositionPacket to the client,
                // which snaps the client's local position back immediately.
                if (data != null) {
                    PositionMoveRotation absolute = new PositionMoveRotation(
                            new Vec3(data.x, data.y, data.z),
                            Vec3.ZERO,
                            data.yaw,
                            data.pitch
                    );
                    player.connection.teleport(absolute, Set.of());
                }

                // Sync the player's inventory to undo any client-side
                // ghost changes (items that appear consumed/placed but
                // were actually blocked on the server).
                player.containerMenu.broadcastChanges();
            }
        });

        LOGGER.info("TimeStop mod initialized — using StunManager with force-position-sync (vanilla-client compatible)");
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
        StunManager.stunForSeconds(target.getUUID(), target, currentTick, durationSeconds);

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
