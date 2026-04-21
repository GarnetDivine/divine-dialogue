package com.divinedialogue;

import com.google.inject.Provides;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Divine Dialogue",
    description = "Mirrors NPC and player dialogue into a configurable, styled overlay window.",
    tags = {"dialogue", "npc", "overlay", "accessibility", "content-creation"}
)
public class DivineDialoguePlugin extends Plugin
{
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private DivineDialogueConfig config;
    @Inject private DivineDialogueOverlay overlay;

    @Getter
    private DialogueState state = DialogueState.IDLE;

    /** The current line the overlay should be rendering, or null when idle. */
    @Getter
    private DialogueLine currentLine;

    /** Tracks what options were present when we entered AWAITING_CHOICE. */
    private List<String> lastOptions = Collections.emptyList();

    /** The text of the last NPC line we displayed, used to detect multi-page dialogue changes. */
    private String lastNpcText = null;
    private String lastNpcName = null;

    /** Pre-loaded fonts keyed by config enum. Populated on startup. */
    private final Map<FontChoice, Font> loadedFonts = new EnumMap<>(FontChoice.class);

    /** Last known overlay location we persisted, used to detect user drags. */
    private Point lastKnownLocation = null;

    @Provides
    DivineDialogueConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DivineDialogueConfig.class);
    }

    @Override
    protected void startUp()
    {
        loadFonts();
        overlay.setPlugin(this);
        overlayManager.add(overlay);
        applySavedPosition();
        resetConversation();
        log.debug("Divine Dialogue started");
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        resetConversation();
        overlay.invalidateGradientCache();
        log.debug("Divine Dialogue stopped");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!DivineDialogueConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        // Handle the "reset position" pseudo-button.
        if ("resetPosition".equals(event.getKey()) && config.resetPosition())
        {
            config.resetPosition(false); // auto-untick
            config.positionX(-1);
            config.positionY(-1);
            overlay.setPreferredLocation(null);
        }
        else if ("positionX".equals(event.getKey()) || "positionY".equals(event.getKey()))
        {
            applySavedPosition();
        }

        // Colors/dimensions may have changed; clear the gradient cache.
        overlay.invalidateGradientCache();
    }

    /**
     * Read X/Y from config and push to the overlay. A value of -1 means "unset" and
     * leaves the overlay at whatever snap-corner RuneLite chose.
     */
    private void applySavedPosition()
    {
        int x = config.positionX();
        int y = config.positionY();
        if (x < 0 || y < 0)
        {
            overlay.setPreferredLocation(null);
            lastKnownLocation = null;
        }
        else
        {
            Point p = new Point(x, y);
            overlay.setPreferredLocation(p);
            lastKnownLocation = p;
        }
    }

    /**
     * Called by the overlay when the user finishes an Alt+drag, so the config sliders
     * reflect where they dropped it.
     */
    public void persistDraggedPosition(Point p)
    {
        if (p == null) return;
        config.positionX(p.x);
        config.positionY(p.y);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Detect Alt+drag moves: if the overlay's current preferred location differs
        // from what we last saved, persist it so the sliders reflect reality.
        Point currentLoc = overlay.getPreferredLocation();
        if (currentLoc != null && !currentLoc.equals(lastKnownLocation))
        {
            lastKnownLocation = new Point(currentLoc);
            // Only persist if this looks like a user drag (not our own config-driven update).
            if (currentLoc.x != config.positionX() || currentLoc.y != config.positionY())
            {
                persistDraggedPosition(currentLoc);
            }
        }

        Widget npcText = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
        Widget npcName = client.getWidget(ComponentID.DIALOG_NPC_NAME);
        Widget playerText = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
        Widget options = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);

        boolean npcVisible = isVisible(npcText);
        boolean playerVisible = isVisible(playerText);
        boolean optionsVisible = isVisible(options);

        // Continue-style dialogue (tap to continue) reuses the same NPC_TEXT widget,
        // so it's handled by the npcVisible branch.

        if (!npcVisible && !playerVisible && !optionsVisible)
        {
            // Nothing on screen — conversation ended or never started.
            if (state != DialogueState.IDLE)
            {
                resetConversation();
            }
            return;
        }

        switch (state)
        {
            case IDLE:
                if (npcVisible)
                {
                    enterNpcSpeaking(npcName, npcText);
                }
                else if (playerVisible)
                {
                    // Some chains open with the player speaking first.
                    enterPlayerSpeaking(playerText);
                }
                else if (optionsVisible)
                {
                    enterAwaitingChoice(options);
                }
                break;

            case NPC_SPEAKING:
                if (optionsVisible)
                {
                    enterAwaitingChoice(options);
                }
                else if (playerVisible)
                {
                    // Rare: NPC line directly followed by a player-spoken line without options.
                    enterPlayerSpeaking(playerText);
                }
                else if (npcVisible)
                {
                    // Multi-page NPC dialogue — text or speaker may have changed.
                    String newText = cleanText(npcText.getText());
                    String newName = npcName != null ? cleanText(npcName.getText()) : null;
                    if (!equalsNullable(newText, lastNpcText) || !equalsNullable(newName, lastNpcName))
                    {
                        enterNpcSpeaking(npcName, npcText);
                    }
                }
                break;

            case AWAITING_CHOICE:
                if (!optionsVisible)
                {
                    // Options closed; the player clicked one. Figure out which.
                    enterPlayerChose();
                }
                else
                {
                    // Options can update (rare, but possible); keep our stored list fresh.
                    lastOptions = extractOptions(options);
                    if (currentLine != null && config.showOptionsInOverlay())
                    {
                        currentLine = DialogueLine.npcWithOptions(
                            currentLine.getSpeaker(), currentLine.getText(), lastOptions);
                    }
                }
                break;

            case PLAYER_CHOSE:
                if (npcVisible)
                {
                    enterNpcSpeaking(npcName, npcText);
                }
                else if (optionsVisible)
                {
                    enterAwaitingChoice(options);
                }
                else if (playerVisible)
                {
                    enterPlayerSpeaking(playerText);
                }
                break;
        }
    }

    // =========================================================================
    // State transitions
    // =========================================================================

    private void enterNpcSpeaking(Widget nameWidget, Widget textWidget)
    {
        String name = nameWidget != null ? cleanText(nameWidget.getText()) : null;
        String text = cleanText(textWidget.getText());

        lastNpcName = name;
        lastNpcText = text;

        if (config.showOptionsInOverlay() && !lastOptions.isEmpty())
        {
            currentLine = DialogueLine.npcWithOptions(name, text, lastOptions);
        }
        else
        {
            currentLine = DialogueLine.npc(name, text);
        }

        state = DialogueState.NPC_SPEAKING;
    }

    private void enterPlayerSpeaking(Widget textWidget)
    {
        String text = cleanText(textWidget.getText());
        currentLine = DialogueLine.player(getLocalPlayerName(), text);
        state = DialogueState.NPC_SPEAKING; // treat player-spoken widgets like NPC lines for flow
    }

    private void enterAwaitingChoice(Widget optionsWidget)
    {
        lastOptions = extractOptions(optionsWidget);

        // Keep current NPC line on screen but optionally attach options to it.
        if (currentLine != null && config.showOptionsInOverlay())
        {
            currentLine = DialogueLine.npcWithOptions(
                currentLine.getSpeaker(), currentLine.getText(), lastOptions);
        }
        state = DialogueState.AWAITING_CHOICE;
    }

    private void enterPlayerChose()
    {
        // We don't get a direct "player clicked option N" event via widget visibility alone,
        // so fall back to the DIALOG_PLAYER_TEXT widget on the next tick. For now, if we have
        // a list of options, we pick a reasonable default behavior: wait for the player text
        // widget to appear next tick and use its contents. If no options were recorded, we
        // simply transition and let the next tick handle it.
        state = DialogueState.PLAYER_CHOSE;

        Widget playerText = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
        if (isVisible(playerText))
        {
            currentLine = DialogueLine.player(getLocalPlayerName(), cleanText(playerText.getText()));
        }
        lastOptions = Collections.emptyList();
    }

    private void resetConversation()
    {
        state = DialogueState.IDLE;
        currentLine = null;
        lastOptions = Collections.emptyList();
        lastNpcText = null;
        lastNpcName = null;
    }

    // =========================================================================
    // Widget helpers
    // =========================================================================

    private static boolean isVisible(Widget w)
    {
        return w != null && !w.isHidden();
    }

    private static List<String> extractOptions(Widget optionsWidget)
    {
        if (optionsWidget == null)
        {
            return Collections.emptyList();
        }
        Widget[] children = optionsWidget.getChildren();
        if (children == null || children.length == 0)
        {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Widget child : children)
        {
            if (child == null) continue;
            String t = child.getText();
            if (t == null || t.isEmpty()) continue;
            String cleaned = cleanText(t);
            if (cleaned.isEmpty()) continue;
            // Skip the "Select an Option" title line that OSRS includes.
            if (cleaned.equalsIgnoreCase("Select an Option")) continue;
            out.add(cleaned);
        }
        return out;
    }

    /**
     * Strips OSRS color/formatting tags (e.g. {@code <col=ffffff>}, {@code <br>}) from
     * raw widget text and converts {@code <br>} to a newline so the overlay can wrap properly.
     */
    static String cleanText(String raw)
    {
        if (raw == null) return "";
        // Replace <br> with a newline first so paragraph breaks survive tag stripping.
        String s = raw.replaceAll("(?i)<br\\s*/?>", "\n");
        // Strip all remaining tags like <col=xxxxxx>, </col>, <img=...>, etc.
        s = s.replaceAll("<[^>]+>", "");
        // Collapse nbsp.
        s = s.replace('\u00A0', ' ');
        return s.trim();
    }

    private static boolean equalsNullable(String a, String b)
    {
        return a == null ? b == null : a.equals(b);
    }

    private String getLocalPlayerName()
    {
        Player p = client.getLocalPlayer();
        if (p != null && p.getName() != null && !p.getName().isEmpty())
        {
            return p.getName();
        }
        return "You";
    }

    // =========================================================================
    // Font loading
    // =========================================================================

    private void loadFonts()
    {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (FontChoice choice : FontChoice.values())
        {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(choice.getResourcePath()))
            {
                if (in == null)
                {
                    log.warn("Font resource missing: {} — falling back to a platform default.", choice.getResourcePath());
                    loadedFonts.put(choice, fallbackFor(choice));
                    continue;
                }
                Font f = Font.createFont(Font.TRUETYPE_FONT, in);
                ge.registerFont(f);
                loadedFonts.put(choice, f);
            }
            catch (Exception e)
            {
                log.warn("Failed to load font {}: {}", choice, e.getMessage());
                loadedFonts.put(choice, fallbackFor(choice));
            }
        }
    }

    private static Font fallbackFor(FontChoice choice)
    {
        switch (choice)
        {
            case SANS_SERIF:
                return new Font(Font.SANS_SERIF, Font.PLAIN, 16);
            case DYSLEXIC:
                // No sane "dyslexia-friendly" platform font; use sans-serif as the closest fallback.
                return new Font(Font.SANS_SERIF, Font.PLAIN, 16);
            case SERIF:
            default:
                return new Font(Font.SERIF, Font.PLAIN, 16);
        }
    }

    /**
     * Returns the loaded (or fallback) base font for the given choice, derived to the
     * configured size and weight. Called by the overlay every frame.
     */
    public Font getConfiguredFont()
    {
        Font base = loadedFonts.getOrDefault(config.fontChoice(), fallbackFor(config.fontChoice()));
        int style = config.fontBold() ? Font.BOLD : Font.PLAIN;
        return base.deriveFont(style, config.fontSize());
    }
}
