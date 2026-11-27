package com.techmonkey.stepper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Stepper implements ModInitializer {
    public static final String MOD_ID = "stepper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SmallStairRegistry.init();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "small_stairs"),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.stepper.small_stairs"))
                        .icon(Stepper::tabIcon)
                        .displayItems((context, output) -> {
                            for (SmallStairRegistry.Entry entry : SmallStairRegistry.entries()) {
                                output.accept(entry.item());
                                output.accept(entry.verticalItem());
                            }
                        })
                        .build());

        // Recipes are generated per block, so they're written as a world datapack once the
        // server (or singleplayer world) has started, then loaded with a resource reload.
        ServerLifecycleEvents.SERVER_STARTED.register(RecipePackWriter::writeAndEnable);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> checkBlockItemLinks());
    }

    /**
     * Safety check: every block that has a BlockItem should still report that item. If any
     * don't, something asked for a block's item before it was registered (the 1.0.0 bug).
     */
    private static void checkBlockItemLinks() {
        int broken = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem blockItem && blockItem.getBlock().asItem() == Items.AIR) {
                broken++;
                if (broken <= 10) {
                    LOGGER.warn("Block {} has item {} but reports no item", BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()), BuiltInRegistries.ITEM.getKey(item));
                }
            }
        }
        if (broken > 0) {
            LOGGER.warn("{} blocks report no item even though they have one", broken);
        } else {
            LOGGER.info("Stepper: {} small stairs and {} vertical small stairs, all block items intact",
                    SmallStairRegistry.entries().size(), SmallStairRegistry.entries().size());
        }
    }

    private static ItemStack tabIcon() {
        for (SmallStairRegistry.Entry entry : SmallStairRegistry.entries()) {
            if (entry.baseId().getNamespace().equals("minecraft") && entry.baseId().getPath().equals("stone_bricks")) {
                return new ItemStack(entry.item());
            }
        }
        return SmallStairRegistry.entries().isEmpty()
                ? new ItemStack(Items.STONE_BRICKS)
                : new ItemStack(SmallStairRegistry.entries().getFirst().item());
    }
}
