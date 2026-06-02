package com.timestop.mixin;

import com.timestop.TimeStopMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that intercepts player movement packets on the server side.
 *
 * <p>When a player has the {@code timestop:stun} status effect active,
 * this mixin cancels the {@code onPlayerMove} handler at HEAD, causing
 * the server to silently drop the movement packet. The player's server-side
 * position remains unchanged; no teleport or rubber-band packet is sent back,
 * so the frozen player does not experience any visual jitter.
 *
 * <p><b>Why drop instead of rubber-band?</b>
 * Dropping the packet is the lightest-weight approach: no additional packets
 * are sent, no position-correction logic runs, and the player is simply
 * ignored for the duration of the effect. The client will continue to render
 * local movement (the player appears to walk on their own screen), but the
 * server and all other players see the affected player standing still.
 * When the effect expires or is cleared, the server's next position update
 * will gently snap the player back to their last server-accepted position.
 *
 * <p><b>Thread safety:</b> The mixin runs on the server tick thread, which
 * is the same thread that processes all network handlers. The
 * {@link #player} shadow field is set by vanilla during handler construction
 * and is never reassigned, so no additional synchronisation is needed.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    /**
     * The player associated with this network handler.
     * Injected by the mixin framework; set once during handler construction
     * by vanilla code and never reassigned.
     */
    @Shadow
    public ServerPlayerEntity player;

    /**
     * Injects at the HEAD of {@code onPlayerMove} with {@code cancellable = true}.
     *
     * <p>If the owning player has the {@code timestop:stun} effect, the
     * callback info is cancelled immediately — the vanilla method body never
     * executes, and the movement packet is silently discarded.
     *
     * <p>The effect check uses {@link ServerPlayerEntity#hasStatusEffect}
     * with the registered effect instance from {@link TimeStopMod#STUN_EFFECT}.
     * This is an O(1) map lookup inside the player's active effect collection.
     *
     * @param packet  the incoming player-move C2S packet (unused; we only
     *                care about the player's status effect state)
     * @param ci      callback info provided by the mixin framework; calling
     *                {@code ci.cancel()} prevents the original method from
     *                executing
     */
    @Inject(
            method = "onPlayerMove",
            at = @At("HEAD"),
            cancellable = true
    )
    private void timestop$blockMovement(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        RegistryEntry<StatusEffect> stunEntry = Registries.STATUS_EFFECT.getEntry(TimeStopMod.STUN_ID)
                .orElse(null);
        if (stunEntry != null && this.player.hasStatusEffect(stunEntry)) {
            ci.cancel();
        }
    }
}
