package com.techmonkey.stepper.client.mixin;

import java.util.concurrent.CompletableFuture;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.resources.model.ClientItemInfoLoader;

import com.techmonkey.stepper.client.StepperClient;

/** Gives every small stair item an item definition, since there's no JSON file for them. */
@Mixin(ClientItemInfoLoader.class)
abstract class ClientItemInfoLoaderMixin {
    @ModifyReturnValue(method = "scheduleLoad", at = @At("RETURN"))
    private static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> stepper$addSmallStairItems(
            CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> original) {
        return original.thenApply(StepperClient::addSmallStairItems);
    }
}
