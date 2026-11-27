package com.techmonkey.stepper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;

/**
 * Creates a small stair and a vertical small stair for every eligible block, from vanilla
 * and from every other mod.
 *
 * <p>Uses Fabric's "all entries" registry hook on items, which visits block items that
 * already exist and every one registered later, so load order doesn't matter. The server and every player
 * must have the same block mods (your PackWiz pack already guarantees that), because the
 * list of generated blocks has to match for registry sync.
 */
public final class SmallStairRegistry {
    public record Entry(Block baseBlock, Identifier baseId, Item baseItem,
            SmallStairBlock block, SmallStairItem item, Identifier id,
            VerticalSmallStairBlock verticalBlock, SmallStairItem verticalItem, Identifier verticalId) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final List<Entry> ENTRIES_VIEW = Collections.unmodifiableList(ENTRIES);
    private static final Set<Block> REGISTERED_BASES = Collections.newSetFromMap(new IdentityHashMap<>());

    private SmallStairRegistry() {
    }

    /** Every small stair created so far, in registration order. */
    public static List<Entry> entries() {
        return ENTRIES_VIEW;
    }

    /**
     * Small stairs are created when a block's <em>item</em> is registered, not when the block
     * is. Mods register a block first and its item a moment later; asking a block for its item
     * before then makes Minecraft cache "no item" for that block permanently, which breaks the
     * other mod (Mo' Glass crashed the creative menu that way in 1.0.0). Waiting for the
     * BlockItem means we never ask too early, and we get the base item directly.
     */
    static void init() {
        RegistryEntryAddedCallback.allEntries(BuiltInRegistries.ITEM, reference -> {
            if (!(reference.value() instanceof BlockItem blockItem) || blockItem instanceof SmallStairItem) {
                return;
            }
            Block baseBlock = blockItem.getBlock();
            Identifier baseId = BuiltInRegistries.BLOCK.getKey(baseBlock);
            if (!REGISTERED_BASES.contains(baseBlock) && isEligible(baseId, baseBlock)) {
                REGISTERED_BASES.add(baseBlock);
                register(baseId, baseBlock, blockItem);
            }
        });
    }

    /**
     * Plain, solid, full-cube blocks only. Skips our own blocks, air, blocks with block
     * entities (chests, machines), anything see-through or oddly shaped (glass, leaves,
     * panes, plants) and unbreakable blocks. Blocks without an item never get here.
     *
     * <p>Must not call {@code asItem()}: see {@link #init()}.
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
        try {
            return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            // Some modded blocks can't answer shape questions this early. Skip them.
            Stepper.LOGGER.debug("Skipping {}: could not check its shape ({})", baseId, e.toString());
            return false;
        }
    }

    private static void register(Identifier baseId, Block baseBlock, Item baseItem) {
        // stepper:<mod>/<block>, e.g. stepper:minecraft/stone
        Identifier id = Identifier.fromNamespaceAndPath(Stepper.MOD_ID, baseId.getNamespace() + "/" + baseId.getPath());
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        SmallStairBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                new SmallStairBlock(baseBlock, copyProperties(baseBlock).setId(blockKey)));
        SmallStairItem item = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new SmallStairItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

        // stepper:vertical/<mod>/<block>, e.g. stepper:vertical/minecraft/stone
        Identifier verticalId = Identifier.fromNamespaceAndPath(Stepper.MOD_ID, "vertical/" + baseId.getNamespace() + "/" + baseId.getPath());
        ResourceKey<Block> verticalBlockKey = ResourceKey.create(Registries.BLOCK, verticalId);
        ResourceKey<Item> verticalItemKey = ResourceKey.create(Registries.ITEM, verticalId);

        VerticalSmallStairBlock verticalBlock = Registry.register(BuiltInRegistries.BLOCK, verticalBlockKey,
                new VerticalSmallStairBlock(baseBlock, copyProperties(baseBlock).setId(verticalBlockKey)));
        SmallStairItem verticalItem = Registry.register(BuiltInRegistries.ITEM, verticalItemKey,
                new SmallStairItem(verticalBlock, new Item.Properties().setId(verticalItemKey).useBlockDescriptionPrefix()));

        ENTRIES.add(new Entry(baseBlock, baseId, baseItem, block, item, id, verticalBlock, verticalItem, verticalId));
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
