package com.divinedialogue;

/**
 * Font choices exposed in config.
 *
 * <p>A null {@code resourcePath} means "use the platform's built-in font family"
 * (looked up by {@code logicalName}) — no TTF/OTF file needed in resources.</p>
 */
public enum FontChoice
{
    SERIF("Serif", null, "Serif"),
    SANS_SERIF("Sans Serif", "fonts/inter.ttf", "SansSerif"),
    PIXEL("Pixel", "fonts/runescape_uf.ttf", "Monospaced"),
    DYSLEXIC("Dyslexic", "fonts/OpenDyslexic-Regular.otf", "SansSerif");

    private final String displayName;
    private final String resourcePath;
    private final String logicalName;

    FontChoice(String displayName, String resourcePath, String logicalName)
    {
        this.displayName = displayName;
        this.resourcePath = resourcePath;
        this.logicalName = logicalName;
    }

    /** Path inside {@code src/main/resources/}, or null if using a platform font. */
    public String getResourcePath()
    {
        return resourcePath;
    }

    /** Platform logical font family used when no resource is bundled (or as fallback). */
    public String getLogicalName()
    {
        return logicalName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
