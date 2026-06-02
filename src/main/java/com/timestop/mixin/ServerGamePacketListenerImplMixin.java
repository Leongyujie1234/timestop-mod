package com.timestop.mixin;

import com.timestop.TimeStopMod;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that intercepts player movement packets on the server side.
 *
 * <p>When a player has the {@code timestop:stun} effect active,
 * this mixin cancels the {@code handleMovePlayer} handler at HEAD, causing
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
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    /**
     * The player associated with this network handler.
     * Injected by the mixin framework; set once during handler construction
     * by vanilla code and never reassigned.
     */
    @Shadow
    public ServerPlayer player;

    /**
     * Injects at the HEAD of {@code handleMovePlayer} with {@code cancellable = true}.
     *
     * <p>If the owning player has the {@code timestop:stun} effect, the
     * callback info is cancelled immediately — the vanilla method body never
     * executes, and the movement packet is silently discarded.
     *
     * @param packet  the incoming player-move C2S packet (unused)
     * @param ci      callback info provided by the mixin framework; calling
     *                {@code ci.cancel()} prevents the original method from
     *                executing
     */
    @Inject(
            method = "handleMovePlayer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void timestop$blockMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (TimeStopMod.STUN_HOLDER != null && this.player.hasEffect(TimeStopMod.STUN_HOLDER)) {
            ci.cancel();
        }
    }
}
