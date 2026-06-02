package com.timestop;

import com.timestop.effect.StunEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the TimeStop Fabric mod.
 *
 * <p>This mod registers a single invisible status effect ({@code timestop:stun})
 * and uses a Mixin to intercept player movement packets on the server side.
 * When the stun effect is active on a player, the server silently drops all
 * movement packets from that player, effectively freezing them in place
 * without any visual feedback (no icon, no particles).
 *
 * <p>Intended usage via Scarpet:
 * <pre>
 * effect give @a[tag=!immune] timestop:stun infinite 0 true
 * </pre>
 */
public class TimeStopMod {

    /** Mod ID used as the namespace for all registered assets. */
    public static final String MOD_ID = "timestop";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * The registered stun effect instance.
     * Stored as a static field so that the mixin can reference it
     * via the corresponding Holder for O(1) lookups.
     */
    public static final MobEffect STUN_EFFECT = new StunEffect();

    /**
     * The Identifier under which the stun effect is registered.
     */
    public static final Identifier STUN_ID = Identifier.fromNamespaceAndPath(MOD_ID, "stun");

    /**
     * The Holder for the stun effect, resolved after registration.
     * This is what {@code ServerPlayer.hasEffect()} expects.
     */
    public static Holder<MobEffect> STUN_HOLDER;

    /**
     * Mod initialiser — called by the Fabric loader during bootstrap.
     *
     * <p>Registers the {@code timestop:stun} status effect in the vanilla
     * registry and caches its Holder for efficient mixin lookups.
     */
    public static void onInitialize() {
        Registry.register(BuiltInRegistries.MOB_EFFECT, STUN_ID, STUN_EFFECT);

        // Cache the Holder reference for the mixin's hot path
        STUN_HOLDER = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(STUN_EFFECT);

        LOGGER.info("TimeStop mod initialized — stun effect registered as {}", STUN_ID);
    }
}
