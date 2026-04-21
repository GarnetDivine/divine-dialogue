package com.divinedialogue;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a local RuneLite client with Divine Dialogue loaded as a built-in plugin.
 *
 * <p>Use this as your IntelliJ Run configuration (right-click the class → Run).
 * No Plugin Hub submission needed for development — the plugin appears in the
 * client's plugin list just like any shipped plugin.</p>
 */
public class DivineDialoguePluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DivineDialoguePlugin.class);
        RuneLite.main(args);
    }
}
