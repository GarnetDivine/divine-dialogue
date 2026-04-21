package com.divinedialogue;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(DivineDialogueConfig.GROUP)
public interface DivineDialogueConfig extends Config
{
    String GROUP = "divinedialogue";

    // =========================================================================
    // Sections
    // =========================================================================

    @ConfigSection(
        name = "Backdrop",
        description = "Four-corner gradient backdrop colors and opacity.",
        position = 0
    )
    String backdropSection = "backdropSection";

    @ConfigSection(
        name = "Border",
        description = "Window border styling.",
        position = 1
    )
    String borderSection = "borderSection";

    @ConfigSection(
        name = "Text",
        description = "Font, size, color, and stroke options.",
        position = 2
    )
    String textSection = "textSection";

    @ConfigSection(
        name = "Window",
        description = "Dialogue window dimensions and padding.",
        position = 3
    )
    String windowSection = "windowSection";

    @ConfigSection(
        name = "Display",
        description = "Dialogue content display options.",
        position = 4
    )
    String displaySection = "displaySection";

    // =========================================================================
    // Backdrop
    // =========================================================================

    @Alpha
    @ConfigItem(
        keyName = "cornerTopLeft",
        name = "Top-Left Color",
        description = "Backdrop gradient color for the top-left corner.",
        section = backdropSection,
        position = 0
    )
    default Color cornerTopLeft()
    {
        return new Color(0x1A, 0x1A, 0x6E, 217); // 217/255 ~= 85%
    }

    @Alpha
    @ConfigItem(
        keyName = "cornerTopRight",
        name = "Top-Right Color",
        description = "Backdrop gradient color for the top-right corner.",
        section = backdropSection,
        position = 1
    )
    default Color cornerTopRight()
    {
        return new Color(0x2E, 0x1A, 0x4E, 217);
    }

    @Alpha
    @ConfigItem(
        keyName = "cornerBottomLeft",
        name = "Bottom-Left Color",
        description = "Backdrop gradient color for the bottom-left corner.",
        section = backdropSection,
        position = 2
    )
    default Color cornerBottomLeft()
    {
        return new Color(0x1A, 0x1A, 0x4E, 217);
    }

    @Alpha
    @ConfigItem(
        keyName = "cornerBottomRight",
        name = "Bottom-Right Color",
        description = "Backdrop gradient color for the bottom-right corner.",
        section = backdropSection,
        position = 3
    )
    default Color cornerBottomRight()
    {
        return new Color(0x3E, 0x1A, 0x3E, 217);
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "backdropOpacity",
        name = "Backdrop Opacity",
        description = "Global opacity multiplier for the backdrop (0–100%).",
        section = backdropSection,
        position = 4
    )
    default int backdropOpacity()
    {
        return 85;
    }

    // =========================================================================
    // Border
    // =========================================================================

    @ConfigItem(
        keyName = "borderEnabled",
        name = "Enable Border",
        description = "Toggle the window border on or off.",
        section = borderSection,
        position = 0
    )
    default boolean borderEnabled()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "borderColor",
        name = "Border Color",
        description = "Color of the window border.",
        section = borderSection,
        position = 1
    )
    default Color borderColor()
    {
        return Color.WHITE;
    }

    @Range(min = 0, max = 5)
    @ConfigItem(
        keyName = "borderThickness",
        name = "Border Thickness",
        description = "Border thickness in pixels (0 disables).",
        section = borderSection,
        position = 2
    )
    default int borderThickness()
    {
        return 1;
    }

    // =========================================================================
    // Text
    // =========================================================================

    @ConfigItem(
        keyName = "fontChoice",
        name = "Font",
        description = "Font family used for dialogue text.",
        section = textSection,
        position = 0
    )
    default FontChoice fontChoice()
    {
        return FontChoice.SERIF;
    }

    @Range(min = 12, max = 24)
    @ConfigItem(
        keyName = "fontSize",
        name = "Font Size",
        description = "Font size in points (12–24).",
        section = textSection,
        position = 1
    )
    default int fontSize()
    {
        return 16;
    }

    @ConfigItem(
        keyName = "fontBold",
        name = "Bold",
        description = "Apply bold weight to all dialogue text.",
        section = textSection,
        position = 2
    )
    default boolean fontBold()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
        keyName = "npcTextColor",
        name = "NPC Text Color",
        description = "Color used for NPC dialogue text.",
        section = textSection,
        position = 3
    )
    default Color npcTextColor()
    {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(
        keyName = "playerTextColor",
        name = "Player Text Color",
        description = "Color used for echoed player dialogue lines.",
        section = textSection,
        position = 4
    )
    default Color playerTextColor()
    {
        return new Color(0xC8, 0xA9, 0x6E);
    }

    @ConfigItem(
        keyName = "strokeEnabled",
        name = "Text Stroke",
        description = "Render an outline/stroke behind text for readability.",
        section = textSection,
        position = 5
    )
    default boolean strokeEnabled()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "strokeColor",
        name = "Stroke Color",
        description = "Color of the text outline.",
        section = textSection,
        position = 6
    )
    default Color strokeColor()
    {
        return Color.BLACK;
    }

    @Range(min = 1, max = 3)
    @ConfigItem(
        keyName = "strokeWidth",
        name = "Stroke Width",
        description = "Width of the text outline in pixels (1–3).",
        section = textSection,
        position = 7
    )
    default int strokeWidth()
    {
        return 1;
    }

    @Alpha
    @ConfigItem(
        keyName = "emoteTextColor",
        name = "Emote Text Color",
        description = "Color used for emote/sprite messages (e.g. \"Arthur's eyes light up\"). These render without a backdrop.",
        section = textSection,
        position = 8
    )
    default Color emoteTextColor()
    {
        return new Color(0xE0, 0xD0, 0xA0);
    }

    @ConfigItem(
        keyName = "emoteItalic",
        name = "Italicize Emotes",
        description = "Render emote/sprite messages in italics.",
        section = textSection,
        position = 9
    )
    default boolean emoteItalic()
    {
        return true;
    }

    // =========================================================================
    // Window
    // =========================================================================

    @Range(min = 200, max = 1200)
    @ConfigItem(
        keyName = "windowWidth",
        name = "Width",
        description = "Dialogue window width in pixels.",
        section = windowSection,
        position = 0
    )
    default int windowWidth()
    {
        return 400;
    }

    @Range(min = 4, max = 24)
    @ConfigItem(
        keyName = "windowPadding",
        name = "Padding",
        description = "Internal padding in pixels.",
        section = windowSection,
        position = 1
    )
    default int windowPadding()
    {
        return 12;
    }

    // =========================================================================
    // Position
    // =========================================================================

    @ConfigItem(
        keyName = "positionX",
        name = "X Position",
        description = "Horizontal position in pixels from the left edge of the game canvas. Updated automatically when you Alt+drag the overlay.",
        section = windowSection,
        position = 2
    )
    default int positionX()
    {
        return -1; // -1 means "unset, use default snap corner"
    }

    @ConfigItem(keyName = "positionX", name = "", description = "")
    void positionX(int x);

    @ConfigItem(
        keyName = "positionY",
        name = "Y Position",
        description = "Vertical position in pixels from the top edge of the game canvas. Updated automatically when you Alt+drag the overlay.",
        section = windowSection,
        position = 3
    )
    default int positionY()
    {
        return -1;
    }

    @ConfigItem(keyName = "positionY", name = "", description = "")
    void positionY(int y);

    @ConfigItem(
        keyName = "resetPosition",
        name = "Reset to Corner",
        description = "Click to clear the saved position and snap the overlay back to the bottom-right corner.",
        section = windowSection,
        position = 4
    )
    default boolean resetPosition()
    {
        return false;
    }

    @ConfigItem(keyName = "resetPosition", name = "", description = "")
    void resetPosition(boolean reset);

    // =========================================================================
    // Display
    // =========================================================================

    @ConfigItem(
        keyName = "conversationMode",
        name = "Conversation Mode",
        description = "Offset NPC dialogue to the left and player dialogue to the right, simulating a two-sided conversation.",
        section = displaySection,
        position = 0
    )
    default boolean conversationMode()
    {
        return false;
    }

    @Range(min = 0, max = 200)
    @ConfigItem(
        keyName = "conversationOffset",
        name = "Conversation Offset",
        description = "How far to nudge NPC (left) and player (right) windows apart in pixels when Conversation Mode is on.",
        section = displaySection,
        position = 1
    )
    default int conversationOffset()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "typewriterMode",
        name = "Typewriter Mode",
        description = "Reveal dialogue text character-by-character instead of all at once.",
        section = displaySection,
        position = 2
    )
    default boolean typewriterMode()
    {
        return false;
    }

    @Range(min = 10, max = 120)
    @ConfigItem(
        keyName = "typewriterSpeed",
        name = "Typewriter Speed",
        description = "Characters per second when Typewriter Mode is on (higher = faster).",
        section = displaySection,
        position = 3
    )
    default int typewriterSpeed()
    {
        return 40;
    }

    @ConfigItem(
        keyName = "typewriterSound",
        name = "Typewriter Sound",
        description = "Play a clicking sound effect as each character is revealed.",
        section = displaySection,
        position = 4
    )
    default TypewriterSound typewriterSound()
    {
        return TypewriterSound.NONE;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "typewriterVolume",
        name = "Sound Volume",
        description = "Volume of the typewriter sound effect (0 = silent, 100 = full).",
        section = displaySection,
        position = 5
    )
    default int typewriterVolume()
    {
        return 60;
    }

    @Range(min = 30, max = 400)
    @ConfigItem(
        keyName = "typewriterSoundInterval",
        name = "Sound Interval (ms)",
        description = "Minimum milliseconds between sound plays. Higher = slower clicking regardless of typing speed.",
        section = displaySection,
        position = 6
    )
    default int typewriterSoundInterval()
    {
        return 80;
    }
}
