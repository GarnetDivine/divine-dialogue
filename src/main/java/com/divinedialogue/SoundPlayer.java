/*
 * Copyright (c) 2026, O3 Studios / Garnet Divine
 * All rights reserved.
 */
package com.divinedialogue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Wrapper around RuneLite's {@link AudioPlayer} for typewriter click playback.
 *
 * <p>Each {@link TypewriterSound} option carries a list of variant sample paths.
 * On every play, we pick one variant at random — this substitutes for the per-play
 * pitch variation we used to do manually, producing the same natural feel without
 * touching {@code javax.sound} APIs directly (banned by the Plugin Hub policy
 * scanner).</p>
 *
 * <p>WAV samples are <em>preloaded into memory</em> on first play and held as
 * {@code byte[]} buffers. On each play, we wrap the bytes in a fresh
 * {@link ByteArrayInputStream} and hand it to {@link AudioPlayer#play(InputStream, float)}.
 * This matters for two reasons:
 * <ul>
 *   <li>The {@code play(Class, String, float)} overload can return "Stream closed"
 *       errors in certain classpath configurations (particularly when resources
 *       live inside a nested jar). Passing our own {@code InputStream} sidesteps
 *       that entire code path.</li>
 *   <li>At ~14 KB per WAV × 8 variants = ~112 KB total, the memory cost is
 *       negligible and we avoid re-reading from disk/jar on every keystroke.</li>
 * </ul>
 *
 * <p>Volume is mapped from the config's 0-100 scale into a dB gain via a
 * logarithmic taper so the slider feels linear to the ear.</p>
 */
@Slf4j
@Singleton
public class SoundPlayer
{
    /** Minimum dB to apply when volume is above zero. Below this, human hearing loses it anyway. */
    private static final float MIN_GAIN_DB = -40f;

    private final AudioPlayer audioPlayer;
    private final Random random = new Random();

    /** Preloaded WAV bytes, keyed by resource path. Populated lazily on first play. */
    private final Map<String, byte[]> audioCache = new HashMap<>();
    /** Paths we've tried and failed to load — skip these instead of retrying forever. */
    private final Set<String> loadFailed = new HashSet<>();

    /** Variants we've already logged a playback failure for — prevents log spam. */
    private final Set<String> pathFailureWarned = new HashSet<>();

    private boolean preloaded = false;

    @Inject
    public SoundPlayer(AudioPlayer audioPlayer)
    {
        this.audioPlayer = audioPlayer;
    }

    /**
     * Plays one randomly-picked variant of the given sound at the specified volume.
     *
     * @param sound  which sound to play; NONE or sounds with no variants are silently ignored
     * @param volume 0–100; 0 is silent (skipped entirely), values are clamped to range
     */
    public void play(TypewriterSound sound, int volume)
    {
        preloadOnce();

        if (sound == null || sound == TypewriterSound.NONE)
        {
            return;
        }
        List<String> variants = sound.getVariantPaths();
        if (variants == null || variants.isEmpty())
        {
            return;
        }
        int clamped = Math.max(0, Math.min(100, volume));
        if (clamped == 0)
        {
            return;
        }

        // Filter to variants that actually loaded successfully.
        List<String> available = new ArrayList<>(variants.size());
        for (String path : variants)
        {
            if (audioCache.containsKey(path))
            {
                available.add(path);
            }
        }
        if (available.isEmpty())
        {
            return;
        }

        String path = available.get(random.nextInt(available.size()));
        byte[] bytes = audioCache.get(path);
        float gain = volumeToGainDb(clamped);

        // Wrap in a fresh ByteArrayInputStream each play — AudioPlayer may close
        // the stream after reading, so it must be re-created per invocation.
        try (InputStream stream = new ByteArrayInputStream(bytes))
        {
            audioPlayer.play(stream, gain);
        }
        catch (Exception e)
        {
            // AudioPlayer.play() declares IOException, UnsupportedAudioFileException,
            // and LineUnavailableException — all from javax.sound.sampled. We catch
            // Exception generically to avoid importing javax.sound.sampled types into
            // plugin code, which is restricted by the Plugin Hub policy scanner.
            if (pathFailureWarned.add(path))
            {
                log.warn("Divine Dialogue: could not play typewriter sound variant {} ({}): {}. "
                    + "Ensure the WAV is 16-bit PCM and your audio drivers are working.",
                    sound, path, e.getMessage());
            }
        }
    }

    /**
     * On first play() call, read every declared variant WAV into an in-memory
     * byte buffer. This pays the I/O cost once at startup and leaves playback
     * as a simple memory-to-mixer copy. Runs exactly once per plugin lifetime.
     */
    private void preloadOnce()
    {
        if (preloaded)
        {
            return;
        }
        preloaded = true;

        List<String> loaded = new ArrayList<>();
        for (TypewriterSound sound : TypewriterSound.values())
        {
            for (String path : sound.getVariantPaths())
            {
                if (audioCache.containsKey(path) || loadFailed.contains(path))
                {
                    continue;
                }
                byte[] bytes = readResource(path);
                if (bytes != null)
                {
                    audioCache.put(path, bytes);
                    loaded.add(path);
                }
                else
                {
                    loadFailed.add(path);
                }
            }
        }
        log.debug("Divine Dialogue: preloaded {} typewriter samples ({}). Failed: {}.",
            loaded.size(), loaded, loadFailed);
    }

    /** Reads a classpath resource into a byte array. Returns null if missing or unreadable. */
    private static byte[] readResource(String path)
    {
        try (InputStream in = SoundPlayer.class.getClassLoader().getResourceAsStream(path))
        {
            if (in == null)
            {
                log.warn("Divine Dialogue: typewriter sound resource missing: {}", path);
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
            {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
        catch (IOException e)
        {
            log.warn("Divine Dialogue: failed to read typewriter sound resource {}: {}",
                path, e.getMessage());
            return null;
        }
    }

    /**
     * Maps the config's 0-100 volume slider to a dB gain value.
     * 0 is handled upstream (skip). 100 → 0 dB (no change). Between, a logarithmic
     * taper: 50 → -6 dB (half perceived loudness), 10 → -20 dB, etc.
     * Clamped at {@link #MIN_GAIN_DB} on the low end.
     */
    private static float volumeToGainDb(int volumePct)
    {
        float pct = volumePct / 100f;
        float db = (float) (20.0 * Math.log10(pct));
        if (db < MIN_GAIN_DB)
        {
            db = MIN_GAIN_DB;
        }
        if (db > 0f)
        {
            db = 0f;
        }
        return db;
    }
}
