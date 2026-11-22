package com.techmonkey.stepper.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockTintsFactory;

import com.techmonkey.stepper.SmallStairBlock;
import com.techmonkey.stepper.SmallStairRegistry;

public class StepperClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        List<SmallStairRegistry.Entry> entries = SmallStairRegistry.entries();

        // Block models: built in code from each base block's model (see SmallStairModel).
        ModelLoadingPlugin.register(pluginContext -> {
            for (SmallStairRegistry.Entry entry : entries) {
                SmallStairUnbakedModel model = new SmallStairUnbakedModel(entry.baseBlock());
                pluginContext.registerBlockStateResolver(entry.block(), resolverContext -> {
                    for (BlockState state : resolverContext.block().getStateDefinition().getPossibleStates()) {
                        resolverContext.setModel(state, model);
                    }
                });
            }
        });

        // Tinting: a small grass block stair (or any tinted modded block) gets the same colors
        // as its base block at the same position. Registered once the client has started, so
        // small stairs for blocks from mods that load after Stepper are included too.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            Block[] blocks = entries.stream().map(SmallStairRegistry.Entry::block).toArray(Block[]::new);
            if (blocks.length > 0) {
                BlockColorRegistry.register(StepperClient::collectBaseTints, blocks);
            }
        });
    }

    private static void collectBaseTints(BlockState state, BlockAndTintGetter level, BlockPos pos, IntList tintValues) {
        if (!(state.getBlock() instanceof SmallStairBlock smallStair)) {
            return;
        }
        BlockState baseState = smallStair.getBaseBlock().defaultBlockState();
        List<BlockTintSource> sources = Minecraft.getInstance().getBlockColors().getTintSources(baseState);
        if (!sources.isEmpty()) {
            for (BlockTintSource source : sources) {
                tintValues.add(source == null ? -1 : source.colorInWorld(baseState, level, pos));
            }
            return;
        }
        BlockTintsFactory baseFactory = BlockColorRegistry.getFactory(baseState);
        if (baseFactory != null) {
            baseFactory.collect(baseState, level, pos, tintValues);
        }
    }

    /**
     * Called from {@code ClientItemInfoLoaderMixin} after item definitions are loaded. Each small
     * stair item doesn't have an item definition file, so it borrows its base block's one.
     *
     * <p>Known limitation for 1.0.0: in inventories and in your hand the item looks like the
     * full base block. Placed small stairs render with the real small stair shape.
     */
    public static ClientItemInfoLoader.LoadedClientInfos addSmallStairItems(ClientItemInfoLoader.LoadedClientInfos loaded) {
        Map<Identifier, ClientItem> contents = new HashMap<>(loaded.contents());
        int added = 0;
        for (SmallStairRegistry.Entry entry : SmallStairRegistry.entries()) {
            if (contents.containsKey(entry.id())) {
                continue; // A resource pack provides a real definition; use it.
            }
            Identifier baseItemId = BuiltInRegistries.ITEM.getKey(entry.baseItem());
            ClientItem baseItem = contents.get(baseItemId);
            if (baseItem != null) {
                contents.put(entry.id(), baseItem);
                added++;
            }
        }
        return added == 0 ? loaded : new ClientItemInfoLoader.LoadedClientInfos(contents);
    }
}
