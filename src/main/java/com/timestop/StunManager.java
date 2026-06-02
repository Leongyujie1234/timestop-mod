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
 * <p>Stun state is tracked per-player UUID and stores the game tick at
 * which the stun expires. Use {@link Long#MAX_VALUE} for an infinite
 * duration.
 */
public final class StunManager {

    /** Map of player UUID → expiry tick. {@link Long#MAX_VALUE} = infinite. */
    private static final Map<UUID, Long> STUNNED_PLAYERS = new ConcurrentHashMap<>();

    private StunManager() { /* utility class */ }

    // ── Query ──────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the player is currently stunned.
     * Does NOT check expiry — call {@link #checkAndExpire(UUID, long)} from
     * a tick handler to clean up expired entries.
     *
     * <p>This method is designed for the movement-packet mixin hot path:
     * it's a simple map lookup with no side effects.
     *
     * @param uuid the player's UUID
     */
    public static boolean isStunned(UUID uuid) {
        return STUNNED_PLAYERS.containsKey(uuid);
    }

    /**
     * Checks if a stunned player's effect has expired and removes it if so.
     *
     * @param uuid the player's UUID
     * @param currentTick the current server game tick
     * @return {@code true} if the player is still stunned after checking expiry
     */
    public static boolean checkAndExpire(UUID uuid, long currentTick) {
        Long expiry = STUNNED_PLAYERS.get(uuid);
        if (expiry == null) return false;
        if (currentTick > expiry) {
            STUNNED_PLAYERS.remove(uuid);
            return false;
        }
        return true;
    }

    // ── Mutate ─────────────────────────────────────────────────────────

    /**
     * Stuns a player until the given tick.
     *
     * @param uuid the player's UUID
     * @param expiryTick the game tick at which the stun expires;
     *                   use {@link Long#MAX_VALUE} for infinite
     */
    public static void stun(UUID uuid, long expiryTick) {
        STUNNED_PLAYERS.put(uuid, expiryTick);
    }

    /**
     * Stuns a player for a given duration in seconds.
     *
     * @param uuid the player's UUID
     * @param currentTick the current server game tick
     * @param durationSeconds duration in seconds; 0 or negative = infinite
     */
    public static void stunForSeconds(UUID uuid, long currentTick, int durationSeconds) {
        if (durationSeconds <= 0) {
            stun(uuid, Long.MAX_VALUE);
        } else {
            stun(uuid, currentTick + (long) durationSeconds * 20);
        }
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

    /**
     * Returns the expiry tick for a stunned player, or null if not stunned.
     */
    public static Long getExpiry(UUID uuid) {
        return STUNNED_PLAYERS.get(uuid);
    }
}
