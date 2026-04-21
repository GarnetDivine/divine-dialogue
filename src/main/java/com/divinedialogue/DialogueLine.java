package com.divinedialogue;

import lombok.Value;

/**
 * A single rendered line in the overlay.
 *
 * <p>{@link #speaker} is the NPC's display name for NPC lines, the local player's
 * name for player echo lines, or null for emote/continue-style lines with no
 * speaker label.</p>
 */
@Value
public class DialogueLine
{
    public enum Kind
    {
        /** Regular NPC dialogue — backdrop, border, NPC text color. */
        NPC,
        /** Player-spoken line or echoed choice — backdrop, border, player text color. */
        PLAYER,
        /** Emote/sprite dialogue (e.g. "Arthur's eyes light up") — no backdrop, no border, own color. */
        EMOTE
    }

    Kind kind;
    String speaker;
    String text;

    public static DialogueLine npc(String speaker, String text)
    {
        return new DialogueLine(Kind.NPC, speaker, text);
    }

    public static DialogueLine player(String speaker, String text)
    {
        return new DialogueLine(Kind.PLAYER, speaker, text);
    }

    public static DialogueLine emote(String text)
    {
        return new DialogueLine(Kind.EMOTE, null, text);
    }

    /** True only for PLAYER kind. */
    public boolean isPlayer()
    {
        return kind == Kind.PLAYER;
    }
}
