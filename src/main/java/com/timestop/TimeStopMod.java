package com.timestop;

import com.timestop.effect.StunEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
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
 *
 * <p>The {@code true} flag in the Scarpet command suppresses particles on the
 * client side; the mod itself suppresses the HUD icon via
 * {@link StunEffect#shouldShowIcon()} and drops movement packets via the mixin.
 */
public class TimeStopMod {

    /** Mod ID used as the namespace for all registered assets. */
    public static final String MOD_ID = "timestop";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * The registered stun effect instance.
     * This is stored as a static field so that the mixin can reference it
     * directly without performing a registry lookup on every packet.
     */
    public static final StatusEffect STUN_EFFECT = new StunEffect();

    /**
     * The Identifier under which the stun effect is registered.
     * Stored as a constant for efficient reuse in the mixin's hot path.
     */
    public static final Identifier STUN_ID = Identifier.of(MOD_ID, "stun");

    /**
     * Mod initialiser — called by the Fabric loader during bootstrap.
     *
     * <p>Registers the {@code timestop:stun} status effect in the vanilla
     * registry. The effect is {@link StatusEffectCategory#NEUTRAL},
     * completely invisible (no icon, no particles), and non-instant.
     */
    public static void onInitialize() {
        Registry.register(Registries.STATUS_EFFECT, STUN_ID, STUN_EFFECT);

        LOGGER.info("TimeStop mod initialized — stun effect registered as {}", STUN_ID);
    }
}
