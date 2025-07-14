package dev.booky.betterview.common.config;
// Created by booky10 in BetterView (14:49 03.06.2025)

import dev.booky.betterview.common.antixray.ReplacementStrategy;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.time.Duration;
import java.util.List;

@NullMarked
@ConfigSerializable
public class BvLevelConfig {

    @Comment("Whether the extended view distance is enabled for this dimension or not")
    private boolean enabled = true;
    @Comment("How many new chunks can be generated for this level in one tick")
    private int chunkGenerationLimit = 2;
    @Comment("How many chunks can be queued per player at once")
    private int chunkQueueSize = 16;
    @Comment("The maximum extended view distance for this level")
    private int viewDistance = 32;
    @Comment("The cache duration for all extended chunks, after which they will be re-build")
    private Duration cacheDuration = Duration.ofMinutes(5L);
    @Comment("Configuration options for a lightweight anti-xray applied to extended chunks")
    private AntiXrayConfig antiXray = new AntiXrayConfig();

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getChunkGenerationLimit() {
        return this.chunkGenerationLimit;
    }

    public int getChunkQueueSize() {
        return this.chunkQueueSize;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public Duration getCacheDuration() {
        return this.cacheDuration;
    }

    public AntiXrayConfig getAntiXray() {
        return this.antiXray;
    }

    @ConfigSerializable
    public static final class AntiXrayConfig {

        @Comment("Whether or not anti-xray is enabled for extended chunks")
        private boolean enabled = false;
        @Comment("The anti-xray engine mode, recommended to be left at \"HIDE\" as this\n"
                + "anti-xray doesn't check whether a block is exposed or not")
        private EngineMode engineMode = EngineMode.HIDE;
        @Comment("The blocks to hide or obfuscate")
        private List<Key> hiddenBlocks = List.of(
                Key.key("stone"), Key.key("deepslate"),
                Key.key("netherrack"), Key.key("end_stone"),
                Key.key("diamond_ore"), Key.key("deepslate_diamond_ore"),
                Key.key("iron_ore"), Key.key("deepslate_iron_ore"),
                Key.key("coal_ore"), Key.key("deepslate_coal_ore"),
                Key.key("emerald_ore"), Key.key("deepslate_emerald_ore"),
                Key.key("copper_ore"), Key.key("deepslate_copper_ore"),
                Key.key("redstone_ore"), Key.key("deepslate_redstone_ore"),
                Key.key("gold_ore"), Key.key("deepslate_gold_ore"),
                Key.key("lapis_ore"), Key.key("deepslate_lapis_ore"),
                Key.key("nether_gold_ore"), Key.key("nether_quartz_ore"),
                Key.key("ancient_debris"), Key.key("raw_copper_block"),
                Key.key("raw_iron_block")
        );

        public boolean isEnabled() {
            return this.enabled;
        }

        public EngineMode getEngineMode() {
            return this.engineMode;
        }

        public List<Key> getHiddenBlocks() {
            return this.hiddenBlocks;
        }

        public enum EngineMode {

            HIDE(ReplacementStrategy::replaceStaticZero),
            OBFUSCATE(ReplacementStrategy::replaceRandom),
            OBFUSCATE_LAYER(ReplacementStrategy::replaceRandomLayered),
            ;

            private final ReplacementStrategy.Ctor strategy;

            EngineMode(ReplacementStrategy.Ctor strategy) {
                this.strategy = strategy;
            }

            public ReplacementStrategy.Ctor getStrategy() {
                return this.strategy;
            }
        }
    }
}
