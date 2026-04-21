# Typewriter Sound Effects

Drop two short WAV files in this directory before building the Plugin Hub release:

| Filename    | Config Option  |
|-------------|----------------|
| `type1.wav` | Option A       |
| `type2.wav` | Option B       |

**Format requirements:**

- `.wav` format (not OGG/MP3 — those need extra decoders)
- Short duration — 50-120ms is ideal. Anything longer will overlap with the next keystroke
- Mono or stereo, 16-bit PCM, any sample rate from 22050 Hz up is fine
- Keep them quiet at source; volume is adjustable via the config slider

**If a file is missing,** the plugin still runs — that specific option just becomes
a silent no-op and logs a warning on startup.

**Pitch variation** is applied automatically on each play (±8% random offset) to
avoid the robotic machine-gun feel of playing the same sample on loop. No need
to provide multiple variants yourself.
