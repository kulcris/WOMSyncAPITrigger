package com.womsheetsbridge;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("womsheetsbridge")
public interface womsheetsbridgeconfig extends Config
{
    @ConfigItem(
            keyName = "webAppUrl",
            name = "Apps Script Web App URL",
            description = "The Apps Script Web App deployment URL (the /exec link)."
    )
    default String webAppUrl()
    {
        return "";
    }

    @ConfigItem(
            keyName = "enabled",
            name = "Enable bridge",
            description = "If disabled, no requests are sent."
    )
    default boolean enabled()
    {
        return true;
    }
    @ConfigItem(
            keyName = "debug",
            name = "Debug logging",
            description = "Logs chat lines during pending WOM sync window and HTTP responses."
    )
    default boolean debug()
    {
        return false;
    }
}
