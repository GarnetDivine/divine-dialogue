package com.divinedialogue;

public enum TypewriterSound
{
    NONE("None", null),
    OPTION_A("Option A", "sounds/type1.wav"),
    OPTION_B("Option B", "sounds/type2.wav");

    private final String displayName;
    private final String resourcePath;

    TypewriterSound(String displayName, String resourcePath)
    {
        this.displayName = displayName;
        this.resourcePath = resourcePath;
    }

    /** Null when no sound should play. */
    public String getResourcePath()
    {
        return resourcePath;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
