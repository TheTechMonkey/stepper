package com.techmonkey.stepper.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Lets the small stair item model find the layers its base model just added. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount")
    int stepper$getActiveLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] stepper$getLayers();
}
