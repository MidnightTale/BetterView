package dev.booky.betterview.nms.v1211;
// Created by booky10 in BetterView (21:19 03.06.2025)

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil;
import com.destroystokyo.paper.util.SneakyThrow;
import com.mojang.serialization.Codec;
import dev.booky.betterview.nms.ReflectionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static dev.booky.betterview.nms.v1211.ChunkWriter.SENDABLE_HEIGHTMAP_TYPES;

@NullMarked
public final class ChunkTagTransformer {

    // see SaveUtil#STARLIGHT_VERSION_TAG
    private static final String STARLIGHT_VERSION_TAG = "starlight.light_version";

    private static final MethodHandle MAKE_BIOME_CODEC_RW = ReflectionUtil.getMethod(
            ChunkSerializer.class, MethodType.methodType(Codec.class, Registry.class), 1);

    private ChunkTagTransformer() {
    }

    @SuppressWarnings("unchecked")
    private static Codec<PalettedContainer<Holder<Biome>>> makeBiomeCodecRW(Registry<Biome> registry) {
        try {
            return (Codec<PalettedContainer<Holder<Biome>>>) MAKE_BIOME_CODEC_RW.invoke(registry);
        } catch (Throwable throwable) {
            SneakyThrow.sneaky(throwable);
            throw new AssertionError();
        }
    }

    public static boolean isChunkLit(CompoundTag tag) {
        ChunkStatus status = ChunkStatus.byName(tag.getString("Status"));
        if (!status.isOrAfter(ChunkStatus.LIGHT)) {
            return false; // not lit yet
        } else if (tag.get(ChunkSerializer.IS_LIGHT_ON_TAG) == null) {
            return false; // light isn't activated
        }
        // check whether starlight version matches
        int lightVersion = tag.getInt(STARLIGHT_VERSION_TAG);
        return lightVersion == SaveUtil.getLightVersion();
    }

    private static boolean extractChunkData(
            ServerLevel level, CompoundTag chunkTag, ChunkPos pos,
            LevelChunkSection[] sections,
            byte[][] blockLight,
            byte @Nullable [][] skyLight
    ) {
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainer<Holder<Biome>>> biomeCodec = makeBiomeCodecRW(biomeRegistry);

        ListTag sectionTags = chunkTag.getList(ChunkSerializer.SECTIONS_TAG, Tag.TAG_COMPOUND);
        int minLightSection = WorldUtil.getMinLightSection(level);

        boolean onlyAir = true;
        for (int i = 0; i < sectionTags.size(); ++i) {
            CompoundTag sectionTag = sectionTags.getCompound(i);
            byte sectionY = sectionTag.getByte("Y");
            int sectionIndex = level.getSectionIndexFromSectionY(sectionY);

            if (sectionIndex >= 0 && sectionIndex < sections.length) {
                BlockState[] presetBlockStates = level.chunkPacketBlockController
                        .getPresetBlockStates(level, pos, sectionY);

                PalettedContainer<BlockState> blocks;
                if (sectionTag.contains("block_states", Tag.TAG_COMPOUND)) {
                    Codec<PalettedContainer<BlockState>> blockStateCodec;
                    if (presetBlockStates != null) {
                        blockStateCodec = PalettedContainer.codecRW(
                                Block.BLOCK_STATE_REGISTRY,
                                BlockState.CODEC,
                                PalettedContainer.Strategy.SECTION_STATES,
                                Blocks.AIR.defaultBlockState(),
                                presetBlockStates
                        );
                    } else {
                        blockStateCodec = ChunkSerializer.BLOCK_STATE_CODEC;
                    }

                    blocks = blockStateCodec
                            .parse(NbtOps.INSTANCE, sectionTag.getCompound("block_states"))
                            .getOrThrow();
                } else {
                    blocks = new PalettedContainer<>(
                            Block.BLOCK_STATE_REGISTRY,
                            Blocks.AIR.defaultBlockState(),
                            PalettedContainer.Strategy.SECTION_STATES,
                            presetBlockStates
                    );
                }

                PalettedContainer<Holder<Biome>> biomes;
                if (sectionTag.contains("biomes", Tag.TAG_COMPOUND)) {
                    biomes = biomeCodec
                            .parse(NbtOps.INSTANCE, sectionTag.getCompound("biomes"))
                            .getOrThrow();
                } else {
                    biomes = new PalettedContainer<>(
                            biomeRegistry.asHolderIdMap(),
                            biomeRegistry.getHolderOrThrow(Biomes.PLAINS),
                            PalettedContainer.Strategy.SECTION_BIOMES,
                            null
                    );
                }

                LevelChunkSection section = new LevelChunkSection(blocks, biomes);
                sections[sectionIndex] = section;

                if (!section.hasOnlyAir()) {
                    onlyAir = false;
                }
            }

            if (sectionTag.contains(ChunkSerializer.BLOCK_LIGHT_TAG, Tag.TAG_BYTE_ARRAY)) {
                blockLight[sectionY - minLightSection] = sectionTag.getByteArray(ChunkSerializer.BLOCK_LIGHT_TAG);
            }

            if (skyLight != null && sectionTag.contains(ChunkSerializer.SKY_LIGHT_TAG, Tag.TAG_BYTE_ARRAY)) {
                skyLight[sectionY - minLightSection] = sectionTag.getByteArray(ChunkSerializer.SKY_LIGHT_TAG);
            }
        }
        return onlyAir;
    }

    private static CompoundTag filterHeightmaps(CompoundTag chunkTag) {
        CompoundTag heightmaps = chunkTag.getCompound(ChunkSerializer.HEIGHTMAPS_TAG);
        if (heightmaps.isEmpty()) {
            return heightmaps;
        }
        CompoundTag filteredHeightmaps = new CompoundTag();
        for (int i = 0, len = SENDABLE_HEIGHTMAP_TYPES.length; i < len; i++) {
            String key = SENDABLE_HEIGHTMAP_TYPES[i].getSerializationKey();
            Tag heightmapsEntry = heightmaps.get(key);
            if (heightmapsEntry != null) {
                filteredHeightmaps.put(key, heightmapsEntry);
            }
        }
        return filteredHeightmaps;
    }

    public static ByteBuf transformToBytesOrEmpty(ServerLevel level, CompoundTag chunkTag, ChunkPos pos) {
        // extract relevant chunk data
        LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];
        byte[][] blockLight = new byte[WorldUtil.getTotalLightSections(level)][];
        byte[][] skyLight = level.dimensionType().hasSkyLight() ? new byte[blockLight.length][] : null;
        boolean onlyAir = extractChunkData(level, chunkTag, pos, sections, blockLight, skyLight);
        if (onlyAir) {
            // empty, skip writing useless packet
            return Unpooled.EMPTY_BUFFER;
        }
        CompoundTag heightmapsTag = filterHeightmaps(chunkTag);
        // delegate to chunk writing method
        return ChunkWriter.writeFull(
                pos.x, pos.z, heightmapsTag,
                sections, blockLight, skyLight
        );
    }
}
