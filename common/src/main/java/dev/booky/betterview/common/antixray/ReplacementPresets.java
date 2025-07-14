package dev.booky.betterview.common.antixray;
// Created by booky10 in BetterView (20:15 16.06.2025)

import org.jspecify.annotations.NullMarked;

import java.util.Arrays;

// https://github.com/PaperMC/Paper/blob/ba7fb23ddd2376079951d1e22f9204d1ed691585/paper-server/src/main/java/io/papermc/paper/antixray/ChunkPacketBlockControllerAntiXray.java#L150-L168
// licensed under the terms of the MIT license
// the code has been adapted to our own structure, additionally all
// returned arrays should be sorted
@NullMarked
@FunctionalInterface
public interface ReplacementPresets {

    static ReplacementPresets createStatic(int... presets) {
        Arrays.sort(presets);
        return __ -> presets;
    }

    static ReplacementPresets createStaticZeroSplit(int[] abovePresets, int[] belowPresets) {
        Arrays.sort(abovePresets);
        Arrays.sort(belowPresets);
        return sectionY -> sectionY < 0 ? belowPresets : abovePresets;
    }

    /**
     * @return a sorted array of preset blockstate ids
     */
    int[] getPresets(int sectionY);
}
