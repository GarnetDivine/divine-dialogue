package com.divinedialogue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class DivineDialogueOverlay extends Overlay
{
    private final DivineDialogueConfig config;
    private final GradientRenderer gradient = new GradientRenderer();

    /** Set by the plugin on startup. */
    private DivineDialoguePlugin plugin;

    // --- Typewriter state.
    /** Identity of the line currently being typed, used to detect when to reset. */
    private String typewriterLineKey = null;
    /** Wall-clock time the current line began revealing. */
    private long typewriterStartMillis = 0L;
    /** Number of characters revealed in the previous render pass, for new-char detection. */
    private int lastRevealedCount = 0;
    /** Last time we played a typewriter sound, for throttling. */
    private long lastSoundMillis = 0L;

    @Inject
    public DivineDialogueOverlay(DivineDialogueConfig config)
    {
        this.config = config;
        setPosition(OverlayPosition.BOTTOM_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        // Let the user drag it with Alt+drag (RuneLite default for movable overlays).
        setMovable(true);
        setResettable(true);
        setSnappable(true);
    }

    public void setPlugin(DivineDialoguePlugin plugin)
    {
        this.plugin = plugin;
    }

    public void invalidateGradientCache()
    {
        gradient.invalidate();
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (plugin == null)
        {
            return null;
        }
        DialogueLine line = plugin.getCurrentLine();
        if (line == null)
        {
            // Clear typewriter state so the next line starts fresh.
            typewriterLineKey = null;
            return null;
        }

        boolean isEmote = line.getKind() == DialogueLine.Kind.EMOTE;
        boolean isPlayer = line.getKind() == DialogueLine.Kind.PLAYER;

        // --- Font selection.
        Font font = plugin.getConfiguredFont();
        if (isEmote && config.emoteItalic())
        {
            font = font.deriveFont(font.getStyle() | Font.ITALIC);
        }
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);

        int width = Math.max(80, config.windowWidth());
        int padding = Math.max(0, config.windowPadding());
        int innerWidth = Math.max(1, width - padding * 2);

        // --- Conversation Mode: decide horizontal offset inside the overlay's bounds.
        // Emotes ignore the mode (they're neither NPC nor player voice).
        boolean conversationActive = config.conversationMode() && !isEmote;
        int conversationOffset = conversationActive ? Math.max(0, config.conversationOffset()) : 0;
        // Total bounding box width accommodates the shift so drag bounds stay predictable.
        int boundsWidth = width + (conversationActive ? conversationOffset * 2 : 0);
        // x-origin where the backdrop/text block starts drawing inside the bounds.
        int contentX;
        if (!conversationActive)
        {
            contentX = 0;
        }
        else if (isPlayer)
        {
            contentX = conversationOffset * 2; // player pushed right
        }
        else
        {
            contentX = 0; // NPC pushed left
        }

        // --- Typewriter: compute how many characters of the body text to reveal.
        String fullBody = line.getText() == null ? "" : line.getText();
        String displayBody = fullBody;
        if (config.typewriterMode() && !isEmote)
        {
            String lineKey = line.getKind() + "|" + line.getSpeaker() + "|" + fullBody;
            long now = System.currentTimeMillis();
            if (!lineKey.equals(typewriterLineKey))
            {
                typewriterLineKey = lineKey;
                typewriterStartMillis = now;
                lastRevealedCount = 0;
            }
            int speed = Math.max(1, config.typewriterSpeed());
            long elapsed = Math.max(0, now - typewriterStartMillis);
            int revealed = (int) Math.min(fullBody.length(), (elapsed * speed) / 1000L);
            displayBody = fullBody.substring(0, revealed);

            // Play a click sound if new characters appeared this frame, we're not
            // past the throttle window, and the newest character isn't whitespace.
            TypewriterSound sound = config.typewriterSound();
            if (sound != null && sound != TypewriterSound.NONE && revealed > lastRevealedCount)
            {
                int interval = Math.max(1, config.typewriterSoundInterval());
                if (now - lastSoundMillis >= interval)
                {
                    char justRevealed = fullBody.charAt(revealed - 1);
                    if (!Character.isWhitespace(justRevealed))
                    {
                        plugin.getSoundPlayer().play(sound, config.typewriterVolume());
                        lastSoundMillis = now;
                    }
                }
            }
            lastRevealedCount = revealed;
        }
        else
        {
            // If typewriter is off, clear the tracker so toggling it back on starts fresh.
            typewriterLineKey = null;
            lastRevealedCount = 0;
        }

        // --- Layout pass: wrap text to figure out final height.
        List<String> wrapped = new ArrayList<>();

        String speakerLine = isEmote ? null : buildSpeakerLine(line);
        if (speakerLine != null)
        {
            wrapped.addAll(wrap(speakerLine, fm, innerWidth));
        }

        wrapped.addAll(wrap(displayBody, fm, innerWidth));

        int lineHeight = fm.getHeight();
        int textBlockHeight = Math.max(lineHeight, wrapped.size() * lineHeight);
        int height = textBlockHeight + padding * 2;

        // --- Paint: backdrop & border skipped for emotes.
        if (!isEmote)
        {
            BufferedImage bg = gradient.render(
                config.cornerTopLeft(),
                config.cornerTopRight(),
                config.cornerBottomLeft(),
                config.cornerBottomRight(),
                config.backdropOpacity(),
                width, height);
            if (bg != null)
            {
                g.drawImage(bg, contentX, 0, null);
            }

            if (config.borderEnabled() && config.borderThickness() > 0)
            {
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke(config.borderThickness()));
                g.setColor(config.borderColor());
                int t = config.borderThickness();
                g.drawRect(contentX + t / 2, t / 2,
                    width - Math.max(1, t), height - Math.max(1, t));
                g.setStroke(old);
            }
        }

        Object oldAntialias = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int x = contentX + padding;
        int y = padding + fm.getAscent();
        int idx = 0;

        if (speakerLine != null)
        {
            Color speakerColor = isPlayer ? config.playerTextColor() : config.npcTextColor();
            Font boldFont = font.deriveFont(Font.BOLD, font.getSize2D());
            g.setFont(boldFont);
            FontMetrics boldFm = g.getFontMetrics(boldFont);
            List<String> speakerWrapped = wrap(speakerLine, boldFm, innerWidth);
            for (String sl : speakerWrapped)
            {
                drawStyledText(g, sl, x, y, speakerColor);
                y += lineHeight;
                idx++;
            }
            g.setFont(font);
        }

        Color bodyColor;
        switch (line.getKind())
        {
            case PLAYER:
                bodyColor = config.playerTextColor();
                break;
            case EMOTE:
                bodyColor = config.emoteTextColor();
                break;
            case NPC:
            default:
                bodyColor = config.npcTextColor();
                break;
        }

        for (int i = idx; i < wrapped.size(); i++)
        {
            drawStyledText(g, wrapped.get(i), x, y, bodyColor);
            y += lineHeight;
        }

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldAntialias);

        return new Dimension(boundsWidth, height);
    }

    private String buildSpeakerLine(DialogueLine line)
    {
        if (line.getSpeaker() == null || line.getSpeaker().isEmpty())
        {
            return null;
        }
        return line.getSpeaker() + ":";
    }

    private void drawStyledText(Graphics2D g, String text, int x, int y, Color fillColor)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        if (config.strokeEnabled() && config.strokeWidth() > 0)
        {
            int sw = config.strokeWidth();
            g.setColor(config.strokeColor());
            // Cheap 8-direction stroke; good enough at 1-3 px and avoids TextLayout overhead.
            for (int dx = -sw; dx <= sw; dx++)
            {
                for (int dy = -sw; dy <= sw; dy++)
                {
                    if (dx == 0 && dy == 0) continue;
                    g.drawString(text, x + dx, y + dy);
                }
            }
        }
        g.setColor(fillColor);
        g.drawString(text, x, y);
    }

    /**
     * Naive word-wrap that also respects embedded newlines (from stripped {@code <br>} tags).
     * Falls back to hard-splitting words longer than the available width.
     */
    static List<String> wrap(String text, FontMetrics fm, int maxWidth)
    {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty())
        {
            out.add("");
            return out;
        }

        String[] paragraphs = text.split("\n", -1);
        for (String paragraph : paragraphs)
        {
            if (paragraph.isEmpty())
            {
                out.add("");
                continue;
            }
            String[] words = paragraph.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String word : words)
            {
                String probe = cur.length() == 0 ? word : cur + " " + word;
                if (fm.stringWidth(probe) <= maxWidth)
                {
                    if (cur.length() == 0)
                    {
                        cur.append(word);
                    }
                    else
                    {
                        cur.append(' ').append(word);
                    }
                }
                else
                {
                    if (cur.length() > 0)
                    {
                        out.add(cur.toString());
                        cur.setLength(0);
                    }
                    // Word itself may be too long; hard-split.
                    if (fm.stringWidth(word) > maxWidth)
                    {
                        StringBuilder chunk = new StringBuilder();
                        for (int i = 0; i < word.length(); i++)
                        {
                            chunk.append(word.charAt(i));
                            if (fm.stringWidth(chunk.toString()) > maxWidth)
                            {
                                chunk.deleteCharAt(chunk.length() - 1);
                                out.add(chunk.toString());
                                chunk.setLength(0);
                                chunk.append(word.charAt(i));
                            }
                        }
                        if (chunk.length() > 0)
                        {
                            cur.append(chunk);
                        }
                    }
                    else
                    {
                        cur.append(word);
                    }
                }
            }
            if (cur.length() > 0)
            {
                out.add(cur.toString());
            }
        }
        return out;
    }
}
