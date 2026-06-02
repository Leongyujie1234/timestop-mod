package com.timestop.mixin;

import com.timestop.StunManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that intercepts player action packets on the server side.
 *
 * <p>When a player is marked as stunned in {@link StunManager}, this mixin
 * cancels the following packet handlers at HEAD, causing the server to
 * silently drop the packets. The stunned player is effectively frozen:
 * they cannot move, place/break blocks, use items, interact with entities,
 * swing their arm, or change their held item.
 *
 * <p><b>What is NOT blocked:</b>
 * <ul>
 *   <li>Chat messages ({@code handleChat}) — stunned players can still type</li>
 *   <li>Commands ({@code handleChatCommand}) — stunned players can still run commands</li>
 *   <li>Disconnect — stunned players can still leave the server</li>
 * </ul>
 *
 * <p><b>Why drop instead of rubber-band?</b>
 * Dropping the packet is the lightest-weight approach: no additional packets
 * are sent, no position-correction logic runs, and the player is simply
 * ignored for the duration of the stun. The client will continue to render
 * local movement (the player appears to walk on their own screen), but the
 * server and all other players see the affected player standing still.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    // ── Movement ────────────────────────────────────────────────────────

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void timestop$blockMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }

    // ── Block break / drop item / stop digging ──────────────────────────

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void timestop$blockPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }

    // ── Use item in hand (right-click air: throw egg, use bucket, etc.) ─

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void timestop$blockUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }

    // ── Use item on block (right-click block: place block, open door, etc.) ─

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void timestop$blockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }

    // ── Arm swing animation ─────────────────────────────────────────────

    @Inject(method = "handleSwing", at = @At("HEAD"), cancellable = true)
    private void timestop$blockSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }

    // ── Interact with entity (right-click mob, attack, etc.) ────────────

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void timestop$blockInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }

    // ── Change held item slot ───────────────────────────────────────────

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void timestop$blockSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }

    // ── Sneak / sprint / jump with horse / start flying ─────────────────

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void timestop$blockPlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
        }
    }
}
