package dev.booky.betterview.common.antixray;
// Created by booky10 in BetterView (19:50 16.06.2025)

import org.jspecify.annotations.NullMarked;

import java.util.concurrent.ThreadLocalRandom;

// see https://github.com/PaperMC/Paper/blob/ba7fb23ddd2376079951d1e22f9204d1ed691585/paper-server/src/main/java/io/papermc/paper/antixray/ChunkPacketBlockControllerAntiXray.java#L232-L272
// licensed under the terms of the MIT license
// other than adapting the code to our own structure, no modifications have been made to the actual logic
@NullMarked
@FunctionalInterface
public interface ReplacementStrategy {

    ReplacementStrategy STATIC_ZERO = () -> 0;

    // Paper's engine-mode 1 (hide ores)
    static ReplacementStrategy replaceStaticZero(int ignoredBlockCount) {
        return STATIC_ZERO;
    }

    // Paper's engine-mode 2 (obfuscate)
    static ReplacementStrategy replaceRandom(int blockCount) {
        return new ReplacementStrategy() {
            private int state;

            {
                while ((this.state = ThreadLocalRandom.current().nextInt()) == 0) ;
            }

            @Override
            public int get() {
                // https://en.wikipedia.org/wiki/Xorshift
                this.state ^= this.state << 13;
                this.state ^= this.state >>> 17;
                this.state ^= this.state << 5;
                // https://www.pcg-random.org/posts/bounded-rands.html
                return (int) ((Integer.toUnsignedLong(this.state) * blockCount) >>> 32);
            }
        };
    }

    // Paper's engine-mode 3 (obfuscate layer)
    static ReplacementStrategy replaceRandomLayered(int blockCount) {
        return new ReplacementStrategy() {
            private int state;
            private int next;

            {
                while ((this.state = ThreadLocalRandom.current().nextInt()) == 0) ;
            }

            @Override
            public void advance() {
                // https://en.wikipedia.org/wiki/Xorshift
                this.state ^= this.state << 13;
                this.state ^= this.state >>> 17;
                this.state ^= this.state << 5;
                // https://www.pcg-random.org/posts/bounded-rands.html
                this.next = (int) ((Integer.toUnsignedLong(this.state) * blockCount) >>> 32);
            }

            @Override
            public int get() {
                return this.next;
            }
        };
    }

    default void advance() {
        // NO-OP
    }

    int get();

    @FunctionalInterface
    interface Ctor {

        ReplacementStrategy construct(int blockCount);
    }
}
