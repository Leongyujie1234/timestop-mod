package com.timestop;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side stun state manager that uses an in-memory map instead of
 * a registered {@link net.minecraft.world.effect.MobEffect}.
 *
 * <p>This approach completely avoids registering anything in
 * {@link net.minecraft.core.registries.BuiltInRegistries}, which means
 * Fabric API's registry sync will never send a {@code timestop:stun} entry
 * to connecting clients. Vanilla clients can therefore join the server
 * without being kicked by "Received a registry entry that is unknown to
 * this client."
 *
 * <p>Stun state is tracked per-player UUID and stores:
 * <ul>
 *   <li>The game tick at which the stun expires</li>
 *   <li>The player's exact position and rotation at the moment of stun</li>
 * </ul>
 */
public final class StunManager {

    /** Stun data for a single player. */
    public static final class StunData {
        /** Tick at which the stun expires. {@link Long#MAX_VALUE} = infinite. */
        public final long expiryTick;
        /** X position when stunned. */
        public final double x;
        /** Y position when stunned. */
        public final double y;
        /** Z position when stunned. */
        public final double z;
        /** Yaw when stunned. */
        public final float yaw;
        /** Pitch when stunned. */
        public final float pitch;

        public StunData(long expiryTick, double x, double y, double z, float yaw, float pitch) {
            this.expiryTick = expiryTick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /** Map of player UUID → stun data. */
    private static final Map<UUID, StunData> STUNNED_PLAYERS = new ConcurrentHashMap<>();

    private StunManager() { /* utility class */ }

    // ── Query ──────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the player is currently stunned.
     */
    public static boolean isStunned(UUID uuid) {
        return STUNNED_PLAYERS.containsKey(uuid);
    }

    /**
     * Returns the stun data for a player, or {@code null} if not stunned.
     */
    public static StunData getStunData(UUID uuid) {
        return STUNNED_PLAYERS.get(uuid);
    }

    // ── Mutate ─────────────────────────────────────────────────────────

    /**
     * Stuns a player, capturing their current position and rotation.
     *
     * @param uuid the player's UUID
     * @param player the server player (for position capture)
     * @param expiryTick the game tick at which the stun expires;
     *                   use {@link Long#MAX_VALUE} for infinite
     */
    public static void stun(UUID uuid, ServerPlayer player, long expiryTick) {
        STUNNED_PLAYERS.put(uuid, new StunData(
                expiryTick,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        ));
    }

    /**
     * Stuns a player for a given duration in seconds, capturing their position.
     *
     * @param uuid the player's UUID
     * @param player the server player (for position capture)
     * @param currentTick the current server game tick
     * @param durationSeconds duration in seconds; 0 or negative = infinite
     */
    public static void stunForSeconds(UUID uuid, ServerPlayer player, long currentTick, int durationSeconds) {
        long expiry = durationSeconds <= 0 ? Long.MAX_VALUE : currentTick + (long) durationSeconds * 20;
        stun(uuid, player, expiry);
    }

    /**
     * Removes the stun from a player.
     *
     * @param uuid the player's UUID
     * @return {@code true} if the player was stunned and is now removed
     */
    public static boolean unstun(UUID uuid) {
        return STUNNED_PLAYERS.remove(uuid) != null;
    }

    /**
     * Removes all stun entries (e.g. on server stop).
     */
    public static void clearAll() {
        STUNNED_PLAYERS.clear();
    }

    // ── Snapshot ───────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of the currently stunned player UUIDs.
     */
    public static Set<UUID> getStunnedPlayers() {
        return Collections.unmodifiableSet(STUNNED_PLAYERS.keySet());
    }
}
