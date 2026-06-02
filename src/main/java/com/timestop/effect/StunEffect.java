package com.timestop.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleEffect;
import org.jetbrains.annotations.Nullable;

/**
 * A completely invisible status effect that serves as a marker for the
 * movement-packet mixin. When this effect is active on a player, the
 * server-side mixin will silently drop all player-move packets, effectively
 * freezing the player in place from the server's perspective.
 *
 * <p>Key design choices:
 * <ul>
 *   <li>{@link StatusEffectCategory#NEUTRAL} — neither beneficial nor harmful,
 *       so it does not influence mob AI or entity attribute calculations.</li>
 *   <li>{@link #createParticle(StatusEffectInstance)} returns {@code null} —
 *       no ambient particles are ever spawned, regardless of the effect
 *       instance's {@code showParticles} flag.</li>
 *   <li>{@link #isInstant()} returns {@code false} — the effect persists over
 *       time and must be explicitly removed or expire.</li>
 *   <li>The colour value {@code 0xFFFFFF} is a no-op placeholder; because the
 *       effect is never rendered, the colour is irrelevant.</li>
 * </ul>
 *
 * <p><b>Icon visibility</b>: In Minecraft 1.21.5, the icon visibility flag
 * ({@code showIcon}) is a per-instance property on {@link StatusEffectInstance},
 * not a property of the effect type itself. The Scarpet command
 * {@code effect give @a[tag=!immune] timestop:stun infinite 0 true} sets
 * {@code hideParticles=true}, which in vanilla also sets {@code showIcon=false},
 * making the effect fully invisible to the affected player.
 */
public class StunEffect extends StatusEffect {

    /**
     * Constructs the stun effect as a NEUTRAL, colourless effect.
     * The colour (0xFFFFFF) is a harmless placeholder — it will never be
     * rendered to the client because both icon and particles are suppressed.
     */
    public StunEffect() {
        super(StatusEffectCategory.NEUTRAL, 0xFFFFFF);
    }

    /**
     * Suppresses all ambient particles for this effect.
     * Returns {@code null} so that no particle effect is ever created,
     * regardless of the effect instance's configuration.
     *
     * @param instance the active effect instance (unused)
     * @return {@code null} — no particles
     */
    @Override
    @Nullable
    public ParticleEffect createParticle(StatusEffectInstance instance) {
        return null;
    }

    /**
     * Indicates that this is a duration-based (non-instant) effect.
     * The Scarpet command uses {@code infinite} duration, which relies on
     * the effect being non-instant to persist across ticks.
     *
     * @return {@code false} always
     */
    @Override
    public boolean isInstant() {
        return false;
    }
}
