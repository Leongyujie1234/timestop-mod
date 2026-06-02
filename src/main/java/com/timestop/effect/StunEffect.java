package com.timestop.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * A completely invisible status effect that serves as a marker for the
 * movement-packet mixin. When this effect is active on a player, the
 * server-side mixin will silently drop all player-move packets, effectively
 * freezing the player in place from the server's perspective.
 *
 * <p>Key design choices:
 * <ul>
 *   <li>{@link MobEffectCategory#NEUTRAL} — neither beneficial nor harmful,
 *       so it does not influence mob AI or entity attribute calculations.</li>
 *   <li>{@link #createParticleOptions(MobEffectInstance)} returns {@code null} —
 *       no ambient particles are ever spawned, regardless of the effect
 *       instance's showParticles flag.</li>
 *   <li>{@link #isInstantenous()} returns {@code false} — the effect persists
 *       over time and must be explicitly removed or expire.</li>
 *   <li>The colour value {@code 0xFFFFFF} is a no-op placeholder; because the
 *       effect is never rendered, the colour is irrelevant.</li>
 * </ul>
 *
 * <p><b>Icon visibility</b>: In Minecraft 26.1, the icon visibility flag
 * ({@code showIcon}) is a per-instance property on {@link MobEffectInstance}.
 * The Scarpet command {@code effect give @a[tag=!immune] timestop:stun infinite 0 true}
 * sets {@code hideParticles=true}, which also sets {@code showIcon=false},
 * making the effect fully invisible to the affected player.
 */
public class StunEffect extends MobEffect {

    /**
     * Constructs the stun effect as a NEUTRAL, colourless effect.
     * The colour (0xFFFFFF) is a harmless placeholder — it will never be
     * rendered to the client because both icon and particles are suppressed.
     */
    public StunEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFFFFFF);
    }

    /**
     * Suppresses all ambient particles for this effect.
     * Returns {@code null} so that no particle effect is ever created.
     *
     * @param instance the active effect instance (unused)
     * @return {@code null} — no particles
     */
    @Override
    @Nullable
    public ParticleOptions createParticleOptions(MobEffectInstance instance) {
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
    public boolean isInstantenous() {
        return false;
    }
}
