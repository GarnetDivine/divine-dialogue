package com.divinedialogue;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for pure-logic helpers. These don't need the RuneLite client at all.
 *
 * <p>For end-to-end testing of widget detection and the state machine, run
 * {@link DivineDialoguePluginTest#main} to launch the full client.</p>
 */
public class DivineDialogueLogicTest
{
    @Test
    public void cleanText_stripsColorTags()
    {
        assertEquals("Hello, adventurer.",
            DivineDialoguePlugin.cleanText("<col=ffffff>Hello, adventurer.</col>"));
    }

    @Test
    public void cleanText_convertsBrToSpace()
    {
        assertEquals("Line one Line two",
            DivineDialoguePlugin.cleanText("Line one<br>Line two"));
    }

    @Test
    public void cleanText_handlesSelfClosingBr()
    {
        assertEquals("Line one Line two",
            DivineDialoguePlugin.cleanText("Line one<br/>Line two"));
    }

    @Test
    public void cleanText_collapsesMultipleBrs()
    {
        assertEquals("A B",
            DivineDialoguePlugin.cleanText("A<br><br>B"));
    }

    @Test
    public void cleanText_nullReturnsEmpty()
    {
        assertEquals("", DivineDialoguePlugin.cleanText(null));
    }

    @Test
    public void wrap_emptyStringProducesOneLine()
    {
        FontMetrics fm = fontMetrics();
        List<String> lines = DivineDialogueOverlay.wrap("", fm, 200);
        assertEquals(1, lines.size());
        assertEquals("", lines.get(0));
    }

    @Test
    public void wrap_shortStringFitsOnOneLine()
    {
        FontMetrics fm = fontMetrics();
        List<String> lines = DivineDialogueOverlay.wrap("Hi.", fm, 500);
        assertEquals(1, lines.size());
        assertEquals("Hi.", lines.get(0));
    }

    @Test
    public void wrap_longStringBreaksIntoMultipleLines()
    {
        FontMetrics fm = fontMetrics();
        String text = "The sun rises over Lumbridge and the sound of distant bells carries across the river.";
        List<String> lines = DivineDialogueOverlay.wrap(text, fm, 120);
        assertTrue("Expected multiple wrapped lines, got: " + lines, lines.size() > 1);
        for (String line : lines)
        {
            assertTrue("Line too wide: '" + line + "'", fm.stringWidth(line) <= 120 + fm.charWidth('W'));
        }
    }

    @Test
    public void wrap_respectsEmbeddedNewlines()
    {
        FontMetrics fm = fontMetrics();
        List<String> lines = DivineDialogueOverlay.wrap("One\nTwo", fm, 500);
        assertEquals(2, lines.size());
        assertEquals("One", lines.get(0));
        assertEquals("Two", lines.get(1));
    }

    @Test
    public void gradient_cachesWhenParametersUnchanged()
    {
        GradientRenderer r = new GradientRenderer();
        Color c = new Color(100, 100, 100, 200);
        BufferedImage a = r.render(c, c, c, c, 85, 100, 50);
        BufferedImage b = r.render(c, c, c, c, 85, 100, 50);
        assertNotNull(a);
        assertSame("Second call should return cached instance", a, b);
    }

    @Test
    public void gradient_regeneratesWhenDimensionsChange()
    {
        GradientRenderer r = new GradientRenderer();
        Color c = new Color(100, 100, 100, 200);
        BufferedImage a = r.render(c, c, c, c, 85, 100, 50);
        BufferedImage b = r.render(c, c, c, c, 85, 200, 50);
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(100, a.getWidth());
        assertEquals(200, b.getWidth());
    }

    @Test
    public void gradient_regeneratesWhenColorChanges()
    {
        GradientRenderer r = new GradientRenderer();
        Color c1 = new Color(100, 100, 100, 200);
        Color c2 = new Color(50, 50, 50, 200);
        BufferedImage a = r.render(c1, c1, c1, c1, 85, 100, 50);
        BufferedImage b = r.render(c2, c1, c1, c1, 85, 100, 50);
        assertNotNull(a);
        assertNotNull(b);
        // Different top-left color means pixel 0,0 should differ.
        assertTrue(a.getRGB(0, 0) != b.getRGB(0, 0));
    }

    private static FontMetrics fontMetrics()
    {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        return img.createGraphics().getFontMetrics(f);
    }
}
