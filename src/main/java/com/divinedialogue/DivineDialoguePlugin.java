package com.divinedialogue;

import com.google.inject.Provides;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
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

    /** The text of the last NPC line we displayed, used to detect multi-page dialogue changes. */
    private String lastNpcText = null;
    private String lastNpcName = null;

    /** Pre-loaded fonts keyed by config enum. Populated on startup. */
    private final Map<FontChoice, Font> loadedFonts = new EnumMap<>(FontChoice.class);

    /** Last known overlay location we persisted, used to detect user drags. */
    private Point lastKnownLocation = null;

    /** Preloads and plays typewriter sound effects with pitch variation. */
    @Getter
    private final SoundPlayer soundPlayer = new SoundPlayer();

    @Provides
    DivineDialogueConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DivineDialogueConfig.class);
    }

    @Override
    protected void startUp()
    {
        loadFonts();
        soundPlayer.loadAll(getClass().getClassLoader());
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
     * Clear the overlay whenever the player leaves the LOGGED_IN state. This catches
     * world hops, logout, login-screen returns, connection drops, and the brief
     * "hopping worlds" screen — situations where {@link #onGameTick} stops firing
     * before the dialogue widgets get a chance to disappear, which would otherwise
     * leave the last line (commonly the "Are you sure you wish to switch to World N?"
     * sprite-text) stuck on screen after the hop completes.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState gs = event.getGameState();
        if (gs != GameState.LOGGED_IN)
        {
            resetConversation();
        }
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
        Widget spriteText = client.getWidget(ComponentID.DIALOG_SPRITE_TEXT);
        Widget doubleSpriteText = client.getWidget(ComponentID.DIALOG_DOUBLE_SPRITE_TEXT);

        boolean npcVisible = isVisible(npcText);
        boolean playerVisible = isVisible(playerText);
        boolean optionsVisible = isVisible(options);
        boolean emoteVisible = isVisible(spriteText) || isVisible(doubleSpriteText);

        // Continue-style dialogue (tap to continue) reuses the same NPC_TEXT widget,
        // so it's handled by the npcVisible branch.

        if (!npcVisible && !playerVisible && !optionsVisible && !emoteVisible)
        {
            // Nothing on screen — conversation ended or never started. We check BOTH
            // state and currentLine here because emote/sprite dialogue (e.g. Motherlode
            // hopper, item use messages) sets currentLine without advancing the state
            // machine out of IDLE. Without the currentLine check, walking away from an
            // emote would leave it stuck on the overlay indefinitely.
            if (state != DialogueState.IDLE || currentLine != null)
            {
                resetConversation();
            }
            return;
        }

        // Emote/sprite messages exist alongside their own widget tree; they don't
        // interact with the NPC/player/option state machine. Handle them first, and
        // if an emote is visible, render it and skip the rest of the state logic.
        if (emoteVisible)
        {
            Widget emoteWidget = isVisible(spriteText) ? spriteText : doubleSpriteText;
            String emoteRaw = emoteWidget.getText();
            String cleaned = cleanText(emoteRaw);
            if (!cleaned.isEmpty())
            {
                // Only update if text changed, so we don't churn the currentLine object every tick.
                if (currentLine == null
                    || currentLine.getKind() != DialogueLine.Kind.EMOTE
                    || !cleaned.equals(currentLine.getText()))
                {
                    currentLine = DialogueLine.emote(cleaned);
                }
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
                    // Options closed; the player clicked one.
                    enterPlayerChose();
                }
                // While options are visible, just keep the current NPC line on screen.
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

        currentLine = DialogueLine.npc(name, text);
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
        // We don't render the option text ourselves — OSRS's own chatbox shows it.
        // We only track the state so we know when the player has clicked a choice.
        state = DialogueState.AWAITING_CHOICE;
    }

    private void enterPlayerChose()
    {
        state = DialogueState.PLAYER_CHOSE;

        // If DIALOG_PLAYER_TEXT is visible this tick, the chosen option's text lives there.
        Widget playerText = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
        if (isVisible(playerText))
        {
            currentLine = DialogueLine.player(getLocalPlayerName(), cleanText(playerText.getText()));
        }
    }

    private void resetConversation()
    {
        state = DialogueState.IDLE;
        currentLine = null;
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

    /**
     * Strips OSRS color/formatting tags (e.g. {@code <col=ffffff>}, {@code <br>}) from
     * raw widget text. {@code <br>} tags become spaces so the overlay's word-wrap can
     * re-flow the text to the configured window width — OSRS inserts these tags based
     * on its own narrow dialogue box and they don't reflect intentional paragraph breaks.
     */
    static String cleanText(String raw)
    {
        if (raw == null) return "";
        // Replace <br> with a space so the wrapper can re-flow at our window width.
        String s = raw.replaceAll("(?i)<br\\s*/?>", " ");
        // Strip all remaining tags like <col=xxxxxx>, </col>, <img=...>, etc.
        s = s.replaceAll("<[^>]+>", "");
        // Collapse nbsp.
        s = s.replace('\u00A0', ' ');
        // Collapse any runs of whitespace introduced by tag stripping.
        s = s.replaceAll("\\s+", " ");
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
            String path = choice.getResourcePath();
            if (path == null)
            {
                // No resource bundled — use the platform logical font directly.
                loadedFonts.put(choice, fallbackFor(choice));
                continue;
            }

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path))
            {
                if (in == null)
                {
                    log.warn("Font resource missing: {} — falling back to {}.", path, choice.getLogicalName());
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
        return new Font(choice.getLogicalName(), Font.PLAIN, 16);
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
