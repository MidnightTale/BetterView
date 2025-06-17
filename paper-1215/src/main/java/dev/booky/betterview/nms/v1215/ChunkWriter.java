package dev.booky.betterview.nms.v1215;
// Created by booky10 in BetterView (20:38 03.06.2025)

import com.destroystokyo.paper.util.SneakyThrow;
import dev.booky.betterview.common.antixray.AntiXrayProcessor;
import dev.booky.betterview.nms.ReflectionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;

@NullMarked
public final class ChunkWriter {

    static final Heightmap.Types[] SENDABLE_HEIGHTMAP_TYPES = Arrays.stream(Heightmap.Types.values())
            .filter(Heightmap.Types::sendToClient).toArray(Heightmap.Types[]::new);
    static final int[] SENDABLE_HEIGHTMAP_TYPE_IDS = Arrays.stream(SENDABLE_HEIGHTMAP_TYPES)
            .mapToInt(Enum::ordinal).toArray();

    private static final MethodHandle GET_NON_EMPTY_BLOCK_COUNT = ReflectionUtil.getGetter(
            LevelChunkSection.class, short.class, 0);

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

    public static long[] @Nullable [] extractHeightmapsData(ChunkAccess chunk) {
        long[] @Nullable [] heightmapsData = new long[SENDABLE_HEIGHTMAP_TYPES.length][];
        for (int i = 0, len = SENDABLE_HEIGHTMAP_TYPES.length; i < len; i++) {
            Heightmap.Types type = SENDABLE_HEIGHTMAP_TYPES[i];
            if (chunk.hasPrimedHeightmap(type)) {
                heightmapsData[i] = chunk.getOrCreateHeightmapUnprimed(type).getRawData();
            }
        }
        return heightmapsData;
    }

    public static ByteBuf writeFullOrEmpty(ChunkAccess chunk, @Nullable AntiXrayProcessor antiXray) {
        if (isEmpty(chunk)) {
            return Unpooled.EMPTY_BUFFER;
        }
        long[] @Nullable [] heightmapsData = extractHeightmapsData(chunk);
        // convert lighting data
        byte[][] blockLight = LightWriter.convertStarlightToBytes(chunk.starlight$getBlockNibbles(), false);
        byte[][] skyLight = LightWriter.convertStarlightToBytes(chunk.starlight$getSkyNibbles(), true);
        // delegate to chunk writing method
        return writeFull(chunk.locX, chunk.locZ, antiXray, chunk.getMinSectionY(),
                heightmapsData, chunk.getSections(), blockLight, skyLight);
    }

    public static ByteBuf writeFull(
            int chunkX, int chunkZ, @Nullable AntiXrayProcessor antiXray, int minSectionY, long[] @Nullable [] heightmapsData,
            LevelChunkSection[] sections, byte[][] blockLight, byte @Nullable [][] skyLight
    ) {
        // allocate pooled buffer
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer();
        try {
            // packet id
            buf.writeByte(NmsAdapter.LEVEL_CHUNK_WITH_LIGHT_PACKET_ID);
            // chunk position
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);
            // write buffer body
            writeFullBody(buf, antiXray, minSectionY, heightmapsData, sections, blockLight, skyLight);
            return buf.retain();
        } finally {
            buf.release();
        }
    }

    private static void writeHeightmaps(ByteBuf buf, long[] @Nullable [] heightmapsData) {
        // count how many heightmap entries are actually present
        int heightmapsLen = heightmapsData.length;
        int heightmapsCount = 0;
        for (int i = 0; i < heightmapsLen; i++) {
            if (heightmapsData[i] != null) {
                heightmapsCount++;
            }
        }
        // write present heightmap entries
        VarInt.write(buf, heightmapsCount);
        for (int i = 0; i < heightmapsLen; i++) {
            long[] data = heightmapsData[i];
            if (data != null) {
                // convert to actual heightmap id and then write
                VarInt.write(buf, SENDABLE_HEIGHTMAP_TYPE_IDS[i]);
                // write raw heightmap data
                FriendlyByteBuf.writeLongArray(buf, data);
            }
        }
    }

    public static void writeFullBody(
            ByteBuf buf, @Nullable AntiXrayProcessor antiXray, int minSectionY,
            long[] @Nullable [] heightmapsData, LevelChunkSection[] sections,
            byte[][] blockLight, byte @Nullable [][] skyLight
    ) {
        // write heightmaps data
        writeHeightmaps(buf, heightmapsData);
        // allocate sub-buffer if we're using anti-xray
        if (antiXray != null) {
            ByteBuf subBuf = PooledByteBufAllocator.DEFAULT.buffer();
            try {
                FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(subBuf);
                for (int i = 0, len = sections.length; i < len; i++) {
                    writeSection(friendlyBuf, sections[i], antiXray, i + minSectionY);
                }
                VarInt.write(buf, subBuf.readableBytes());
                buf.writeBytes(subBuf);
            } finally {
                subBuf.release();
            }
        } else {
            // calculate serialized size of chunk data
            int serializedSize = 0;
            for (int i = 0, len = sections.length; i < len; i++) {
                LevelChunkSection section = sections[i];
                serializedSize += section.getSerializedSize()
                        // fix https://bugs.mojang.com/browse/MC-296121
                        - VarInt.getByteSize(section.states.data.storage().getRaw().length)
                        - VarInt.getByteSize(((PalettedContainer<?>) section.getBiomes()).data.storage().getRaw().length);
            }
            // directly write chunk data, don't create useless sub-buffer
            VarInt.write(buf, serializedSize);
            int expectedWriterIndex = buf.writerIndex() + serializedSize;
            FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buf);
            for (int i = 0, len = sections.length; i < len; i++) {
                sections[i].write(friendlyBuf, null, 0);
            }
            // ensure the vanilla client can read this data
            if (buf.writerIndex() != expectedWriterIndex) {
                throw new IllegalStateException("Expected writer index to be at "
                        + expectedWriterIndex + ", got " + buf.writerIndex());
            }
        }
        // skip writing block entity list
        VarInt.write(buf, 0);
        // write light data
        LightWriter.writeLightData(buf, blockLight, skyLight);
    }

    private static void writeSection(
            FriendlyByteBuf buf, LevelChunkSection section,
            AntiXrayProcessor antiXray, int sectionY
    ) {
        try {
            buf.writeShort((short) GET_NON_EMPTY_BLOCK_COUNT.invoke(section));
        } catch (Throwable throwable) {
            SneakyThrow.sneaky(throwable);
        }

        int preReaderIndex = buf.readerIndex();
        int preWriterIndex = buf.writerIndex();
        section.states.write(buf, null, 0);
        // move to before states are written for anti-xray to be able to read the states
        buf.readerIndex(preWriterIndex);
        // run anti-xray processing
        antiXray.process(buf, sectionY, false);
        // reset reader index
        buf.readerIndex(preReaderIndex);

        section.getBiomes().write(buf, null, 0);
    }
}
