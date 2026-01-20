package com.womsheetsbridge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class WomSheetsBridgePluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(WomSheetsBridgePlugin.class);
        RuneLite.main(args);
    }
}