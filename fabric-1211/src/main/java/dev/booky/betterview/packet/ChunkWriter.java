package dev.booky.betterview.packet;
// Created by booky10 in BetterView (20:38 03.06.2025)

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

@NullMarked
public final class ChunkWriter {

    static final Heightmap.Types[] SENDABLE_HEIGHTMAP_TYPES = Arrays.stream(Heightmap.Types.values())
            .filter(Heightmap.Types::sendToClient).toArray(Heightmap.Types[]::new);

    private ChunkWriter() {
    }

    private static boolean isEmpty(ChunkAccess chunk) {
        if (chunk instanceof EmptyLevelChunk) {
            return true;
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0, len = sections.length; i < len; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    public static CompoundTag extractHeightmapTags(ChunkAccess chunk) {
        CompoundTag heightmapsTag = new CompoundTag();
        for (int i = 0, len = SENDABLE_HEIGHTMAP_TYPES.length; i < len; i++) {
            Heightmap.Types type = SENDABLE_HEIGHTMAP_TYPES[i];
            if (chunk.hasPrimedHeightmap(type)) {
                long[] heightmapData = chunk.getOrCreateHeightmapUnprimed(type).getRawData();
                heightmapsTag.put(type.getSerializationKey(), new LongArrayTag(heightmapData));
            }
        }
        return heightmapsTag;
    }

    public static ByteBuf writeFullOrEmpty(ChunkAccess chunk) {
        if (isEmpty(chunk)) {
            return Unpooled.EMPTY_BUFFER;
        }
        CompoundTag heightmapTags = extractHeightmapTags(chunk);
        // convert lighting data
        byte[][] blockLight = LightWriter.convertStarlightToBytes(((StarlightChunk) chunk).starlight$getBlockNibbles(), false);
        byte[][] skyLight = LightWriter.convertStarlightToBytes(((StarlightChunk) chunk).starlight$getSkyNibbles(), true);
        // delegate to chunk writing method
        ChunkPos chunkPos = chunk.getPos();
        return writeFull(chunk.getPos().x, chunkPos.z,
                heightmapTags, chunk.getSections(), blockLight, skyLight);
    }

    public static ByteBuf writeFull(
            int chunkX, int chunkZ, CompoundTag heightmapsTag,
            LevelChunkSection[] sections, byte[][] blockLight, byte @Nullable [][] skyLight
    ) {
        // allocate pooled buffer
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer();
        try {
            // packet id
            buf.writeByte(PacketUtil.LEVEL_CHUNK_WITH_LIGHT_PACKET_ID);
            // chunk position
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);
            // write buffer body
            writeFullBody(buf, heightmapsTag, sections, blockLight, skyLight);
            return buf.retain();
        } finally {
            buf.release();
        }
    }

    public static void writeFullBody(
            ByteBuf buf,
            CompoundTag heightmapsTag, LevelChunkSection[] sections,
            byte[][] blockLight, byte @Nullable [][] skyLight
    ) {
        // calculate serialized size of chunk data
        int serializedSize = 0;
        for (int i = 0, len = sections.length; i < len; i++) {
            serializedSize += sections[i].getSerializedSize();
        }
        // write heightmaps nbt tag
        FriendlyByteBuf.writeNbt(buf, heightmapsTag);
        // directly write chunk data, don't create useless sub-buffer
        VarInt.write(buf, serializedSize);
        int expectedWriterIndex = buf.writerIndex() + serializedSize;
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buf);
        for (int i = 0, len = sections.length; i < len; i++) {
            sections[i].write(friendlyBuf);
        }
        // ensure the vanilla client can read this data
        if (buf.writerIndex() != expectedWriterIndex) {
            throw new IllegalStateException("Expected writer index to be at "
                    + expectedWriterIndex + ", got " + buf.writerIndex());
        }
        // skip writing block entity list
        VarInt.write(buf, 0);
        // write light data
        LightWriter.writeLightData(buf, blockLight, skyLight);
    }
}
