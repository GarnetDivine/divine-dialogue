package com.divinedialogue;

/**
 * Tracks the current state of an in-game dialogue conversation.
 * Transitions are driven by widget visibility changes observed in
 * {@link DivineDialoguePlugin#onGameTick}.
 */
public enum DialogueState
{
    /** No dialogue widgets are visible. */
    IDLE,

    /** An NPC (or continue-style) dialogue widget is visible and being mirrored. */
    NPC_SPEAKING,

    /** The options widget is visible; waiting for the player to pick. */
    AWAITING_CHOICE,

    /** The player just chose an option; echo it before the next NPC line. */
    PLAYER_CHOSE
}
