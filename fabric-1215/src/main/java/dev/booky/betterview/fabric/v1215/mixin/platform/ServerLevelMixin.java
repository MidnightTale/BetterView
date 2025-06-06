package dev.booky.betterview.fabric.v1215.mixin.platform;
// Created by booky10 in BetterView (04:24 05.06.2025)

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.libs.ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.github.benmanes.caffeine.cache.LoadingCache;
import dev.booky.betterview.common.ChunkCacheEntry;
import dev.booky.betterview.common.config.BvLevelConfig;
import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.util.BetterViewUtil;
import dev.booky.betterview.common.util.ChunkTagResult;
import dev.booky.betterview.common.util.McChunkPos;
import dev.booky.betterview.fabric.v1215.BetterViewMod;
import dev.booky.betterview.fabric.v1215.packet.ChunkTagTransformer;
import dev.booky.betterview.fabric.v1215.packet.ChunkWriter;
import dev.booky.betterview.fabric.v1215.packet.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import net.kyori.adventure.key.Key;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@NullMarked
@Implements(@Interface(iface = LevelHook.class, prefix = "betterview$"))
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements WorldGenLevel {

    @Shadow @Final
    private ServerChunkCache chunkSource;

    @Unique
    private @MonotonicNonNull LoadingCache<McChunkPos, ChunkCacheEntry> cache;
    @Unique
    private boolean voidWorld;
    @Unique
    private @MonotonicNonNull ByteBuf emptyChunkData;
    @Unique
    private @MonotonicNonNull AtomicInteger generatedChunks;

    public ServerLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates); // dummy ctor
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void postInit(CallbackInfo ci) {
        this.emptyChunkData = PacketUtil.buildEmptyChunkData((ServerLevel) (Object) this);
        this.cache = BetterViewUtil.buildCache((LevelHook) this);
        this.voidWorld = PacketUtil.checkVoidWorld((ServerLevel) (Object) this);
        this.generatedChunks = new AtomicInteger();
    }

    public CompletableFuture<@Nullable ByteBuf> betterview$getCachedChunkBuf(McChunkPos chunkPos) {
        NewChunkHolder holder = ((ChunkSystemServerLevel) this).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos.getKey());
        if (holder == null) {
            return CompletableFuture.completedFuture(null);
        }
        ChunkAccess access = holder.getChunkIfPresent(ChunkStatus.LIGHT);
        if (access == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> ChunkWriter.writeFullOrEmpty(access));
    }

    public CompletableFuture<@Nullable ChunkTagResult> betterview$readChunk(McChunkPos chunkPos) {
        ChunkPos vanillaPos = new ChunkPos(chunkPos.getX(), chunkPos.getZ());
        return this.chunkSource.chunkMap.read(vanillaPos).thenApplyAsync(tag -> {
            if (tag.isEmpty()) {
                return null;
            } else if (!ChunkTagTransformer.isChunkLit(tag.get())) {
                return ChunkTagResult.EMPTY;
            }
            ByteBuf chunkBuf = ChunkTagTransformer.transformToBytesOrEmpty((ServerLevel) (Object) this, tag.get(), vanillaPos);
            return new ChunkTagResult(chunkBuf);
        });
    }

    public CompletableFuture<ByteBuf> betterview$loadChunk(int chunkX, int chunkZ) {
        CompletableFuture<ByteBuf> future = new CompletableFuture<>();
        PlatformHooks.get().scheduleChunkLoad((ServerLevel) (Object) this, chunkX, chunkZ, true,
                ChunkStatus.LIGHT, true, Priority.LOW,
                chunk -> future.completeAsync(() -> ChunkWriter.writeFullOrEmpty(chunk)));
        return future;
    }

    public boolean betterview$checkChunkGeneration() {
        if (BetterViewMod.INSTANCE.getManager().checkChunkGeneration()) {
            return this.generatedChunks.getAndIncrement() <= ((LevelHook) this).getConfig().getChunkGenerationLimit();
        }
        return false;
    }

    public void betterview$resetChunkGeneration() {
        this.generatedChunks.set(0);
    }

    public ByteBuf betterview$getEmptyChunkBuf(McChunkPos chunkPos) {
        ByteBuf posBuf = BetterViewUtil.encodeChunkPos(chunkPos.getX(), chunkPos.getZ());
        return PooledByteBufAllocator.DEFAULT.compositeBuffer(3)
                .addComponent(true, PacketUtil.LEVEL_CHUNK_WITH_LIGHT_PACKET_ID_BUF.retainedSlice())
                .addComponent(true, posBuf)
                .addComponent(true, this.emptyChunkData.retainedSlice());
    }

    public boolean betterview$isVoidWorld() {
        return this.voidWorld;
    }

    public Object betterview$dimension() {
        return this.dimension();
    }

    public BvLevelConfig betterview$getConfig() {
        return BetterViewMod.INSTANCE.getManager().getConfig(this.dimension().location());
    }

    public LoadingCache<McChunkPos, ChunkCacheEntry> betterview$getChunkCache() {
        return this.cache;
    }

    public Key betterview$getName() {
        return this.dimension().location();
    }
}
