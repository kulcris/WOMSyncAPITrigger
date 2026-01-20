package com.womsheetsbridge;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("womsheetsbridge")
public interface WomSheetsBridgeConfig extends Config
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
            keyName = "sharedSecret",
            name = "Shared secret",
            description = "Must match SHARED_SECRET in your Apps Script."
    )
    default String sharedSecret()
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
}
