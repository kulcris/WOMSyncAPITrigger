package com.womsheetsbridge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class womsheetsbridgeplugintest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(womsheetsbridgeplugin.class);
        RuneLite.main(args);
    }
}