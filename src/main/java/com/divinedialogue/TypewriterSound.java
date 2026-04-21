/*
 * Copyright (c) 2026, O3 Studios / Garnet Divine
 * All rights reserved.
 */
package com.divinedialogue;

import java.util.Collections;
import java.util.List;

/**
 * Config-facing enum for the typewriter sound selector. Each option (besides
 * {@link #NONE}) resolves to a list of sample variant paths; one is picked at
 * random on each play to give the typewriter a natural, non-robotic feel.
 *
 * <p>Variants are named {@code typeNa.wav, typeNb.wav, typeNc.wav, typeNd.wav}
 * where N is the option number. All four files are expected to exist on the
 * classpath; {@link SoundPlayer} logs a warning and skips a missing variant
 * without breaking the others.</p>
 */
public enum TypewriterSound
{
    NONE("None", Collections.emptyList()),
    OPTION_A("Option A", List.of(
        "sounds/type1a.wav",
        "sounds/type1b.wav",
        "sounds/type1c.wav",
        "sounds/type1d.wav"
    )),
    OPTION_B("Option B", List.of(
        "sounds/type2a.wav",
        "sounds/type2b.wav",
        "sounds/type2c.wav",
        "sounds/type2d.wav"
    ));

    private final String displayName;
    private final List<String> variantPaths;

    TypewriterSound(String displayName, List<String> variantPaths)
    {
        this.displayName = displayName;
        this.variantPaths = variantPaths;
    }

    /**
     * Returns the list of classpath resource paths this option can play.
     * Empty for {@link #NONE}. Never null.
     */
    public List<String> getVariantPaths()
    {
        return variantPaths;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
