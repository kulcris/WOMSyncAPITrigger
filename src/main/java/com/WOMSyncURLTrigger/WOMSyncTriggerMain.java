package com.womsheetsbridge;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.Notifier;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@PluginDescriptor(
        name = "WOM → Sheets Bridge",
        description = "Triggers a Google Sheets Apps Script when the 'Sync WOM Group' button is pressed.",
        tags = {"wise old man", "wom", "google sheets", "apps script"}
)
public class WomSheetsBridgePlugin extends Plugin
{
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // The WOM plugin’s button click generally surfaces as a menu option containing this text.
    // (The official WOM RuneLite plugin adds a 'Sync WOM Group' button to clan settings.) :contentReference[oaicite:1]{index=1}
    private static final String SYNC_OPTION_TEXT = "Sync WOM Group";

    @Inject private WomSheetsBridgeConfig config;
    @Inject private Notifier notifier;

    // RuneLite commonly uses OkHttp; we can create our own client safely.
    private final OkHttpClient http = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Provides
    WomSheetsBridgeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(WomSheetsBridgeConfig.class);
    }

    @Override
    protected void shutDown()
    {
        executor.shutdownNow();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!config.enabled())
        {
            return;
        }

        final String option = event.getMenuOption();
        if (option == null)
        {
            return;
        }

        // Be forgiving: different RL builds/plugins sometimes add color tags or prefixes.
        // So we match "contains" not equals.
        if (!option.contains(SYNC_OPTION_TEXT))
        {
            return;
        }

        final String url = config.webAppUrl();
        final String secret = config.sharedSecret();

        if (url == null || url.isBlank() || secret == null || secret.isBlank())
        {
            notifier.notify("WOM→Sheets Bridge: configure Web App URL + secret first.");
            return;
        }

        // Fire request off-thread
        executor.submit(() -> triggerAppsScript(url, secret));
    }

    private void triggerAppsScript(String webAppUrl, String secret)
    {
        // Minimal payload. You can add more fields if you want.
        final String payload = "{\"secret\":\"" + escapeJson(secret) + "\"}";

        RequestBody body = RequestBody.create(payload, JSON);
        Request req = new Request.Builder()
                .url(webAppUrl)
                .post(body)
                .build();

        try (Response res = http.newCall(req).execute())
        {
            if (!res.isSuccessful())
            {
                log.warn("Apps Script call failed: HTTP {} {}", res.code(), res.message());
                notifier.notify("WOM→Sheets Bridge: Sheets trigger failed (HTTP " + res.code() + ").");
                return;
            }

            notifier.notify("WOM→Sheets Bridge: Sheets script triggered.");
        }
        catch (IOException ex)
        {
            log.warn("Apps Script call error", ex);
            notifier.notify("WOM→Sheets Bridge: error calling Sheets endpoint.");
        }
    }

    private static String escapeJson(String s)
    {
        // Simple safe escaping for quotes/backslashes in the secret
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
