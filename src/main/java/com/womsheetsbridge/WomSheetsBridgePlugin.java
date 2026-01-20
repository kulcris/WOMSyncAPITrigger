package com.womsheetsbridge;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.Notifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private static final String SYNC_OPTION_TEXT = "Sync WOM Group";

    @Inject private WomSheetsBridgeConfig config;
    @Inject private Notifier notifier;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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
        if (option == null || !option.contains(SYNC_OPTION_TEXT))
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

        executor.submit(() -> triggerAppsScript(url, secret));
    }

    private void triggerAppsScript(String webAppUrl, String secret)
    {
        final String payload = "{\"secret\":\"" + escapeJson(secret) + "\"}";

        try
        {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webAppUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (res.statusCode() < 200 || res.statusCode() >= 300)
            {
                log.warn("Apps Script call failed: HTTP {} body={}", res.statusCode(), res.body());
                notifier.notify("WOM→Sheets Bridge: Sheets trigger failed (HTTP " + res.statusCode() + ").");
                return;
            }

            notifier.notify("WOM→Sheets Bridge: Sheets script triggered.");
        }
        catch (Exception ex)
        {
            log.warn("Apps Script call error", ex);
            notifier.notify("WOM→Sheets Bridge: error calling Sheets endpoint.");
        }
    }

    private static String escapeJson(String s)
    {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
