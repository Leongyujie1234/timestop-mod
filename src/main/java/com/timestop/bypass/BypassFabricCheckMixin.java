package com.timestop.bypass;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Mixin that intercepts the Fabric API handshake disconnect.
 *
 * <p>When Fabric API is installed on the server, it sends a configuration task
 * to connecting clients. If the client doesn't have Fabric, the server
 * disconnects them with "This server requires Fabric Loader and Fabric API
 * installed on your client!". This mixin intercepts that disconnect and
 * gracefully finishes the configuration task instead, allowing vanilla
 * clients to join.
 *
 * <p>Uses reflection to access Fabric API's internal task fields, since
 * we don't depend on Fabric API at compile time.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class BypassFabricCheckMixin {

    @Unique
    private static final Logger BYPASS_LOGGER = LoggerFactory.getLogger("TimeStop-Bypass");

    @Unique
    private static Field currentTaskField;

    @Unique
    private static Method finishCurrentTaskMethod;

    @Unique
    private static boolean reflectionInitialized = false;

    @Unique
    private static boolean reflectionFailed = false;

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void timestop$interceptFabricDisconnect(Component reason, CallbackInfo ci) {
        // Only act on the configuration phase
        if (!((Object) this instanceof ServerConfigurationPacketListenerImpl handler)) return;

        String message = reason.getString();
        if (message.contains("Fabric") && (message.contains("requires") || message.contains("install"))) {
            BYPASS_LOGGER.warn("Intercepted Fabric handshake failure — allowing vanilla client to join");
            ci.cancel();
            this.timestop$completeTaskAndProgress(handler);
        }
    }

    @Unique
    private void timestop$completeTaskAndProgress(ServerConfigurationPacketListenerImpl handler) {
        try {
            if (!reflectionInitialized) {
                timestop$prepareReflection();
                reflectionInitialized = true;
            }

            if (reflectionFailed) {
                BYPASS_LOGGER.error("Reflection setup failed — cannot bypass safely");
                return;
            }

            Object currentTask = currentTaskField.get(handler);
            if (currentTask != null) {
                Method typeMethod = currentTask.getClass().getMethod("type");
                Object taskType = typeMethod.invoke(currentTask);

                finishCurrentTaskMethod.setAccessible(true);
                finishCurrentTaskMethod.invoke(handler, taskType);

                BYPASS_LOGGER.info("Successfully bypassed Fabric configuration task: {}", taskType);
            } else {
                BYPASS_LOGGER.warn("No active task found — configuration may proceed naturally");
            }
        } catch (Exception e) {
            BYPASS_LOGGER.error("Failed to bypass configuration task", e);
        }
    }

    @Unique
    private void timestop$prepareReflection() {
        try {
            Class<?> clazz = ServerConfigurationPacketListenerImpl.class;

            try {
                currentTaskField = clazz.getDeclaredField("currentTask");
            } catch (NoSuchFieldException e) {
                BYPASS_LOGGER.error("Could not find currentTask field");
                reflectionFailed = true;
                return;
            }
            currentTaskField.setAccessible(true);

            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals("finishCurrentTask")) {
                    finishCurrentTaskMethod = m;
                    break;
                }
            }

            if (finishCurrentTaskMethod == null) {
                BYPASS_LOGGER.error("Could not find finishCurrentTask method");
                reflectionFailed = true;
                return;
            }

            BYPASS_LOGGER.info("Reflection setup successful");
        } catch (Exception e) {
            reflectionFailed = true;
            BYPASS_LOGGER.error("Reflection preparation failed", e);
        }
    }
}
