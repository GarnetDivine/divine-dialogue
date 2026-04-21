package com.divinedialogue;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Renders a four-corner bilinearly-interpolated gradient backdrop.
 *
 * <p>The image is cached and only regenerated when the corner colors,
 * global opacity, or target dimensions change. At typical dialogue window
 * sizes (~400×150 px) the per-pixel interpolation is trivial, but caching
 * still avoids reallocating a BufferedImage every frame.</p>
 */
public class GradientRenderer
{
    private BufferedImage cached;

    private Color cTL, cTR, cBL, cBR;
    private int cachedOpacity = -1;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    /**
     * Returns the cached gradient image, regenerating it if any parameter has changed.
     *
     * @param tl       top-left corner color (alpha channel respected)
     * @param tr       top-right corner color
     * @param bl       bottom-left corner color
     * @param br       bottom-right corner color
     * @param opacity  global opacity 0-100 applied multiplicatively on top of per-color alpha
     * @param width    target image width in pixels (must be > 0)
     * @param height   target image height in pixels (must be > 0)
     */
    public BufferedImage render(Color tl, Color tr, Color bl, Color br,
                                int opacity, int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return null;
        }

        if (cached != null
            && width == cachedWidth
            && height == cachedHeight
            && opacity == cachedOpacity
            && equals(tl, cTL) && equals(tr, cTR)
            && equals(bl, cBL) && equals(br, cBR))
        {
            return cached;
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        float opacityMul = Math.max(0, Math.min(100, opacity)) / 100f;

        // Pre-extract channels as floats to avoid per-pixel getRed/getGreen overhead
        float tlR = tl.getRed(),   tlG = tl.getGreen(),   tlB = tl.getBlue(),   tlA = tl.getAlpha();
        float trR = tr.getRed(),   trG = tr.getGreen(),   trB = tr.getBlue(),   trA = tr.getAlpha();
        float blR = bl.getRed(),   blG = bl.getGreen(),   blB = bl.getBlue(),   blA = bl.getAlpha();
        float brR = br.getRed(),   brG = br.getGreen(),   brB = br.getBlue(),   brA = br.getAlpha();

        float wMinus1 = Math.max(1, width - 1);
        float hMinus1 = Math.max(1, height - 1);

        for (int y = 0; y < height; y++)
        {
            float v = y / hMinus1;
            for (int x = 0; x < width; x++)
            {
                float u = x / wMinus1;

                // Interpolate top and bottom edges, then interpolate vertically.
                float topR = lerp(tlR, trR, u), topG = lerp(tlG, trG, u), topB = lerp(tlB, trB, u), topA = lerp(tlA, trA, u);
                float botR = lerp(blR, brR, u), botG = lerp(blG, brG, u), botB = lerp(blB, brB, u), botA = lerp(blA, brA, u);

                int r = clamp((int) lerp(topR, botR, v));
                int g = clamp((int) lerp(topG, botG, v));
                int b = clamp((int) lerp(topB, botB, v));
                int a = clamp((int) (lerp(topA, botA, v) * opacityMul));

                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, argb);
            }
        }

        cached = img;
        cTL = tl; cTR = tr; cBL = bl; cBR = br;
        cachedOpacity = opacity;
        cachedWidth = width;
        cachedHeight = height;
        return cached;
    }

    /** Force the cache to invalidate; next render() call will rebuild unconditionally. */
    public void invalidate()
    {
        cached = null;
        cTL = cTR = cBL = cBR = null;
        cachedOpacity = -1;
        cachedWidth = -1;
        cachedHeight = -1;
    }

    private static float lerp(float a, float b, float t)
    {
        return a + (b - a) * t;
    }

    private static int clamp(int v)
    {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private static boolean equals(Color a, Color b)
    {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getRGB() == b.getRGB();
    }
}
