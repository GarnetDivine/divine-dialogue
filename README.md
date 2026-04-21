# Divine Dialogue

Mirrors NPC and player dialogue into a configurable, styled overlay window separate from the default OSRS chatbox. Built for Let's Play recordings and streams where the chatbox is typically obscured to protect private chats, but equally useful as an accessibility aid for anyone who wants larger, more readable dialogue text in a position they choose.

Second entry in the Divine plugin family, following [Divine: Skies](https://github.com/GarnetDivine/divine-skies).

## Features

- **Four-corner gradient backdrop** with per-corner color, alpha, and a global opacity slider
- **Configurable border** — thickness, color, and on/off toggle
- **Four font choices** — Serif, Sans Serif, Pixel, OpenDyslexic — plus size, bold, and italicized-emote toggle
- **Separate text colors** for NPC lines, player-echoed choices, and emote/sprite messages
- **Text stroke/outline** with configurable color and thickness for readability over any background
- **Emote and sprite message support** — lines like "Arthur's eyes light up" or "You check the bag..." render as floating text without a backdrop
- **Conversation Mode** offsets NPC dialogue left and player dialogue right for a two-sided feel
- **Typewriter Mode** reveals text character-by-character at a configurable speed
- **Typewriter sound effects** (optional) — two bundled click samples with random pitch variation
- **Free positioning** — Alt+drag the overlay, or use X/Y sliders for pixel-precise placement
- **Reset to Corner** button to clear the saved position and snap back to the bottom-right

## Compliance

Read-only plugin. No game state modification, no automation, no menu injection, no input interception, no network calls. All dialogue choices remain in the default chatbox where you click them as normal — Divine Dialogue only mirrors the text for display purposes. Uses only RuneLite's standard overlay system and widget APIs.

## Credits

Built by **Garnet Divine** of **O3 Studios**.

Fonts bundled under the SIL Open Font License. See `src/main/resources/fonts/README.md` for per-font attribution.

## Support

Bug reports and feature requests: https://github.com/GarnetDivine/divine-dialogue/issues

## License

BSD 2-Clause. See `LICENSE`.
