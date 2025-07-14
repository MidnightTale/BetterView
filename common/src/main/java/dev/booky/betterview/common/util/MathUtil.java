package dev.booky.betterview.common.util;
// Created by booky10 in BetterView (21:10 16.06.2025)

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MathUtil {

    private MathUtil() {
    }

    public static int log2(int i) {
        // https://stackoverflow.com/a/3305710
        return 31 - Integer.numberOfLeadingZeros(i);
    }

    public static int ceilLog2(int i) {
        // https://stackoverflow.com/a/22027270
        boolean pow2 = i != 0 && (i & -i) == i;
        // if the number is a power of two, don't add one; otherwise,
        // we want to get to the ceiling!
        return log2(i) + (pow2 ? 0 : 1);
    }
}
