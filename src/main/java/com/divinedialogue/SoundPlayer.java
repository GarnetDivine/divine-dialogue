/*
 * Copyright (c) 2025, O3 Studios / Garnet Divine
 * All rights reserved.
 */
package com.divinedialogue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;

/**
 * Preloads typewriter WAV samples into byte buffers at startup, then plays them
 * on demand with a small random pitch offset (via sample-rate shift) and a configurable
 * volume.
 *
 * <p>Playback strategy: try a non-blocking {@link Clip} at the (optionally pitch-shifted)
 * format first. If the mixer rejects the format — common on Windows drivers that only
 * advertise a few discrete sample rates — fall back to the original unshifted format,
 * then to a {@link SourceDataLine} path as a last resort. A typewriter that clicks
 * without pitch variation is better than a silent one.</p>
 *
 * <p>Load failures and playback failures are logged at WARN once per sound, so
 * breakage surfaces in the RuneLite log even at default log levels.</p>
 */
@Slf4j
public class SoundPlayer
{
    /** Max fractional pitch offset. 0.08 = ±8% which sounds like natural typewriter key variation. */
    private static final float PITCH_RANGE = 0.08f;

    /** Decoded raw audio bytes per sound, keyed by config enum. Null when not loaded. */
    private final Map<TypewriterSound, byte[]> audioBytes = new EnumMap<>(TypewriterSound.class);
    /** Original AudioFormat for each loaded sample. */
    private final Map<TypewriterSound, AudioFormat> formats = new EnumMap<>(TypewriterSound.class);
    /** Sounds we've already logged a playback failure for — prevents log spam. */
    private final Set<TypewriterSound> failureWarned = EnumSet.noneOf(TypewriterSound.class);
    /** Sounds we've already logged the first play attempt for — diagnostic, prevents spam. */
    private final Set<TypewriterSound> firstPlayLogged = EnumSet.noneOf(TypewriterSound.class);

    private final Random random = new Random();

    /**
     * Loads all non-NONE typewriter sounds from the classloader. Safe to call on plugin
     * startup. If a resource is missing or malformed, a warning is logged and that sound
     * entry is skipped (attempts to play it later become no-ops).
     */
    public void loadAll(ClassLoader classLoader)
    {
        for (TypewriterSound sound : TypewriterSound.values())
        {
            String path = sound.getResourcePath();
            if (path == null)
            {
                continue;
            }
            try (InputStream in = classLoader.getResourceAsStream(path))
            {
                if (in == null)
                {
                    log.warn("Typewriter sound missing: {} — option disabled.", path);
                    continue;
                }
                // Decode once so we can replay without re-reading the file each time.
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(readAll(in))))
                {
                    AudioFormat fmt = ais.getFormat();
                    byte[] raw = readAll(ais);
                    audioBytes.put(sound, raw);
                    formats.put(sound, fmt);
                    log.debug("Loaded typewriter sound {}: {} bytes, {}", path, raw.length, fmt);
                }
            }
            catch (UnsupportedAudioFileException e)
            {
                log.warn("Typewriter sound {} is not a supported WAV format ({}). "
                    + "Re-export as 16-bit PCM WAV in Audacity.", path, e.getMessage());
            }
            catch (IOException e)
            {
                log.warn("Failed to read typewriter sound {}: {}", path, e.getMessage());
            }
            catch (Exception e)
            {
                log.warn("Unexpected error loading {}: {}", path, e.getMessage());
            }
        }

        // Startup summary — logged at DEBUG so developers can confirm which samples
        // successfully loaded if they turn on debug logging, but doesn't pollute the
        // default user log.
        log.debug("Divine Dialogue typewriter sounds loaded: {} / {}. Formats: {}",
            audioBytes.size(),
            TypewriterSound.values().length - 1, // minus NONE
            formats);
    }

    /**
     * Plays the given sound once with a random pitch offset in the range ±{@value #PITCH_RANGE}.
     * On any playback failure, falls back to unshifted format, then to SourceDataLine.
     *
     * @param sound  which sound to play; NONE or unloaded sounds are silently ignored
     * @param volume 0–100; values outside this range are clamped
     */
    public void play(TypewriterSound sound, int volume)
    {
        if (sound == null || sound == TypewriterSound.NONE)
        {
            return;
        }
        byte[] raw = audioBytes.get(sound);
        AudioFormat baseFormat = formats.get(sound);
        if (raw == null || baseFormat == null)
        {
            return;
        }

        // Once per sound, log that we've been asked to play it. DEBUG-level so it
        // doesn't spam user logs, but stays available for diagnosing issues.
        if (firstPlayLogged.add(sound))
        {
            log.debug("Divine Dialogue: first play attempt for {} (volume={}, format={})",
                sound, volume, baseFormat);
        }

        // Shift sample rate by a small random amount — audio hardware interprets
        // the same bytes faster/slower, which shifts perceived pitch.
        float shift = 1.0f + ((random.nextFloat() * 2f - 1f) * PITCH_RANGE);
        AudioFormat shifted = new AudioFormat(
            baseFormat.getEncoding(),
            baseFormat.getSampleRate() * shift,
            baseFormat.getSampleSizeInBits(),
            baseFormat.getChannels(),
            baseFormat.getFrameSize(),
            baseFormat.getFrameRate() * shift,
            baseFormat.isBigEndian()
        );

        // Attempt 1: Clip with pitch-shifted format. Fastest path when it works.
        if (tryPlayClip(raw, shifted, volume))
        {
            return;
        }

        // Attempt 2: Clip with original format (pitch variation lost, but audible).
        if (tryPlayClip(raw, baseFormat, volume))
        {
            return;
        }

        // Attempt 3: SourceDataLine with original format. Most compatible, spawns
        // a throwaway thread per play (fine at ~5 plays/sec throttle).
        if (tryPlaySourceDataLine(sound, raw, baseFormat, volume))
        {
            return;
        }

        // All paths exhausted — warn once per sound so the log doesn't flood.
        if (failureWarned.add(sound))
        {
            log.warn("Typewriter sound {} could not be played by any audio path. "
                + "Check that your audio drivers support {} Hz, {}-bit, {}-channel PCM.",
                sound, baseFormat.getSampleRate(), baseFormat.getSampleSizeInBits(), baseFormat.getChannels());
        }
    }

    private boolean tryPlayClip(byte[] raw, AudioFormat format, int volume)
    {
        try
        {
            AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(raw), format,
                raw.length / Math.max(1, format.getFrameSize()));

            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            applyClipVolume(clip, volume);

            // Release clip resources when playback finishes so we don't leak them.
            clip.addLineListener(evt -> {
                if (evt.getType() == LineEvent.Type.STOP)
                {
                    clip.close();
                }
            });

            clip.start();
            return true;
        }
        catch (LineUnavailableException | IllegalArgumentException e)
        {
            // LineUnavailable: driver can't open a line at this format (common with
            // pitch-shifted sample rates). IllegalArgumentException: format outright
            // unsupported. Either way, caller will try the next fallback.
            log.debug("Clip path rejected format {}: {}", format, e.getMessage());
            return false;
        }
        catch (Exception e)
        {
            log.debug("Clip path unexpected failure: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryPlaySourceDataLine(TypewriterSound sound, byte[] raw, AudioFormat format, int volume)
    {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        if (!AudioSystem.isLineSupported(info))
        {
            return false;
        }
        try
        {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            applyLineVolume(line, volume);
            line.start();

            // Write + drain + close on a background thread so we don't block the render pass.
            // At ~5 plays/sec throttle and ~80ms clip length, these threads come and go quickly.
            Thread t = new Thread(() -> {
                try
                {
                    line.write(raw, 0, raw.length);
                    line.drain();
                }
                catch (Exception ignored) { }
                finally
                {
                    try { line.close(); } catch (Exception ignored) { }
                }
            }, "DivineDialogue-Sound-" + sound);
            t.setDaemon(true);
            t.start();
            return true;
        }
        catch (Exception e)
        {
            log.debug("SourceDataLine path failed: {}", e.getMessage());
            return false;
        }
    }

    private static void applyClipVolume(Clip clip, int volumePct)
    {
        applyGain(clip.isControlSupported(FloatControl.Type.MASTER_GAIN)
            ? (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)
            : null, volumePct);
    }

    private static void applyLineVolume(SourceDataLine line, int volumePct)
    {
        applyGain(line.isControlSupported(FloatControl.Type.MASTER_GAIN)
            ? (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN)
            : null, volumePct);
    }

    private static void applyGain(FloatControl gain, int volumePct)
    {
        if (gain == null)
        {
            return;
        }
        float pct = Math.max(0, Math.min(100, volumePct)) / 100f;
        // Map 0..1 linearly into a sensible dB range. -40dB ≈ inaudible, 0dB ≈ unchanged.
        // Anything above 0 risks clipping, so we cap at 0.
        float db;
        if (pct <= 0f)
        {
            db = gain.getMinimum();
        }
        else
        {
            db = (float) (20.0 * Math.log10(pct));
            if (db < gain.getMinimum()) db = gain.getMinimum();
            if (db > gain.getMaximum()) db = gain.getMaximum();
        }
        gain.setValue(db);
    }

    private static byte[] readAll(InputStream in) throws IOException
    {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) != -1)
        {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
