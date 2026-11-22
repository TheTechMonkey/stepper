package com.techmonkey.stepper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;

/**
 * Creates a small stair for every eligible block, from vanilla and from every other mod.
 *
 * <p>Uses Fabric's "all entries" registry hook, which visits blocks that already exist and
 * every block registered later, so load order doesn't matter. The server and every player
 * must have the same block mods (your PackWiz pack already guarantees that), because the
 * list of generated blocks has to match for registry sync.
 */
public final class SmallStairRegistry {
    public record Entry(Block baseBlock, Identifier baseId, SmallStairBlock block, SmallStairItem item, Identifier id) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final List<Entry> ENTRIES_VIEW = Collections.unmodifiableList(ENTRIES);

    private SmallStairRegistry() {
    }

    /** Every small stair created so far, in registration order. */
    public static List<Entry> entries() {
        return ENTRIES_VIEW;
    }

    static void init() {
        RegistryEntryAddedCallback.allEntries(BuiltInRegistries.BLOCK, reference -> {
            Block baseBlock = reference.value();
            Identifier baseId = BuiltInRegistries.BLOCK.getKey(baseBlock);
            if (isEligible(baseId, baseBlock)) {
                register(baseId, baseBlock);
            }
        });
    }

    /**
     * Plain, solid, full-cube blocks only. Skips our own blocks, air, blocks with block
     * entities (chests, machines), anything see-through or oddly shaped (glass, leaves,
     * panes, plants), unbreakable blocks, and blocks with no item to craft from.
     */
    private static boolean isEligible(Identifier baseId, Block baseBlock) {
        if (baseId.getNamespace().equals(Stepper.MOD_ID)) {
            return false;
        }
        if (baseBlock instanceof EntityBlock) {
            return false;
        }

        BlockState state = baseBlock.defaultBlockState();
        if (state.isAir() || !state.canOcclude() || state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }
        if (baseBlock.defaultDestroyTime() < 0) {
            return false;
        }
        if (baseBlock.asItem() == Items.AIR) {
            return false;
        }

        try {
            return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            // Some modded blocks can't answer shape questions this early. Skip them.
            Stepper.LOGGER.debug("Skipping {}: could not check its shape ({})", baseId, e.toString());
            return false;
        }
    }

    private static void register(Identifier baseId, Block baseBlock) {
        // stepper:<mod>/<block>, e.g. stepper:minecraft/stone
        Identifier id = Identifier.fromNamespaceAndPath(Stepper.MOD_ID, baseId.getNamespace() + "/" + baseId.getPath());
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        SmallStairBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                new SmallStairBlock(baseBlock, copyProperties(baseBlock).setId(blockKey)));
        SmallStairItem item = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new SmallStairItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

        ENTRIES.add(new Entry(baseBlock, baseId, block, item, id));
    }

    /**
     * Looks and sounds like the base block, mines and resists explosions like it. Tool
     * requirements are deliberately not copied here; SmallStairBlock checks them against the
     * base block instead.
     */
    private static BlockBehaviour.Properties copyProperties(Block baseBlock) {
        BlockState baseState = baseBlock.defaultBlockState();
        int lightEmission = baseState.getLightEmission();

        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(baseBlock.defaultMapColor())
                .strength(baseBlock.defaultDestroyTime(), baseBlock.getExplosionResistance())
                .sound(baseState.getSoundType())
                .lightLevel(state -> lightEmission)
                .friction(baseBlock.getFriction())
                .speedFactor(baseBlock.getSpeedFactor())
                .jumpFactor(baseBlock.getJumpFactor())
                .instrument(baseState.instrument());
        if (baseState.ignitedByLava()) {
            properties.ignitedByLava();
        }
        return properties;
    }
}
