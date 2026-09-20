package com.sterul.opencookbookapiserver.services.selection;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Breaks ties between equally good recipes, reproducibly: the same seed and subject give the same draw. */
public final class Jitter {

    private Jitter() {
    }

    public static long newSeed() {
        return ThreadLocalRandom.current().nextLong();
    }

    /** A draw from 0 to 1. */
    public static double draw(long seed, Object... judged) {
        return new Random(Objects.hash(seed, Objects.hash(judged))).nextDouble();
    }
}
