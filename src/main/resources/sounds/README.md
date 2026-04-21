# Typewriter Sound Effects

Drop eight short WAV files in this directory — four variants per option. On each
keystroke, one variant is picked at random, producing a natural, non-robotic
typewriter sound without needing per-play pitch manipulation.

| Filename        | Config Option  |
|-----------------|----------------|
| `type1a.wav`    | Option A       |
| `type1b.wav`    | Option A       |
| `type1c.wav`    | Option A       |
| `type1d.wav`    | Option A       |
| `type2a.wav`    | Option B       |
| `type2b.wav`    | Option B       |
| `type2c.wav`    | Option B       |
| `type2d.wav`    | Option B       |

**Format requirements:**

- `.wav` format (not OGG/MP3 — RuneLite's AudioPlayer doesn't bundle those decoders)
- **16-bit PCM** — 24-bit and 32-bit-float WAVs will fail to play on many systems
- Short duration — 50-120 ms is ideal; anything longer overlaps with the next keystroke
- Mono or stereo, any sample rate from 22050 Hz up is fine
- Keep them quiet at source; volume is adjustable via the config slider (0–100)

**Variant design tip:** the four variants for a given option should sound
broadly similar (all snappy/mechanical, or all soft/muted) but differ in subtle
ways — slight attack differences, small timbre shifts, or minor amplitude
variations. Think of them as four recordings of the same key on the same
typewriter, not four completely different keys.

**If a variant is missing** at runtime, the plugin logs a warning the first time
that specific variant fails to play, then continues. The other variants for that
option keep working. The user won't hear the missing variant but everything else
behaves normally.

**Playback goes through RuneLite's `AudioPlayer`** — that means volume settings
respect any RuneLite-level audio preferences, and we avoid the driver
compatibility headaches that come with direct `javax.sound` use.
