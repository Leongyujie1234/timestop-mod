package com.timestop.mixin;

import com.timestop.StunManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
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
 * <p>When a player is stunned:
 * <ul>
 *   <li>All interaction packets are cancelled so the server ignores them</li>
 *   <li>For interaction packets that would change client state (item use,
 *       block place, held item change), the player's inventory is immediately
 *       synced back to prevent ghost items/blocks on the client</li>
 * </ul>
 *
 * <p>The tick handler in {@link com.timestop.TimeStopMod} force-teleports
 * the player back to their capture position every tick, so the client
 * cannot drift away even temporarily.
 *
 * <p><b>What is NOT blocked:</b>
 * <ul>
 *   <li>Chat messages — stunned players can still type</li>
 *   <li>Commands — stunned players can still run commands</li>
 *   <li>Disconnect — stunned players can still leave the server</li>
 * </ul>
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
            // Sync inventory to prevent ghost items (e.g. dropped items reappearing)
            this.player.containerMenu.broadcastChanges();
        }
    }

    // ── Use item in hand (right-click air: throw egg, use bucket, etc.) ─

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void timestop$blockUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
            // Sync inventory to undo client-side item consumption
            this.player.containerMenu.broadcastChanges();
        }
    }

    // ── Use item on block (right-click block: place block, open door, etc.) ─

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void timestop$blockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
            // Sync inventory to undo client-side block placement / item use
            this.player.containerMenu.broadcastChanges();
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
            // Sync inventory to undo client-side item use on entities
            this.player.containerMenu.broadcastChanges();
        }
    }

    // ── Change held item slot ───────────────────────────────────────────

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void timestop$blockSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (StunManager.isStunned(this.player.getUUID())) {
            ci.cancel();
            // Force-sync the correct held slot back to the client
            this.player.containerMenu.broadcastChanges();
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
