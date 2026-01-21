package com.womsheetsbridge;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@PluginDescriptor(
        name = "WOM → Sheets Bridge",
        description = "Triggers a Google Sheets Apps Script after the Wise Old Man 'Sync WOM Group' completes.",
        tags = {"wom", "wise old man", "google sheets", "apps script"}
)
public class womsheetsbridgeplugin extends Plugin
{
    private static final String SYNC_OPTION_TEXT = "Sync WOM Group";
    private static final long PENDING_WINDOW_MS = 120_000; // 2 minutes
    private static final long DEBOUNCE_MS = 10_000;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Inject private womsheetsbridgeconfig config;
    @Inject private Notifier notifier;

    // Injected by RuneLite; do not construct your own OkHttpClient/Builder
    @Inject private OkHttpClient okHttpClient;

    // Kept, but no longer used for HTTP (enqueue is async). Safe to remove later if you want.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean syncPending = false;
    private volatile long syncPendingUntilMs = 0L;
    private volatile long lastTriggerAtMs = 0L;

    @Provides
    womsheetsbridgeconfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(womsheetsbridgeconfig.class);
    }

    @Override
    protected void shutDown()
    {
        executor.shutdownNow();
        syncPending = false;
        syncPendingUntilMs = 0L;
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

        syncPending = true;
        syncPendingUntilMs = System.currentTimeMillis() + PENDING_WINDOW_MS;

        if (config.debug())
        {
            log.info("[WOMSheetsBridge] Armed pending window for {}ms", PENDING_WINDOW_MS);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!config.enabled() || !syncPending)
        {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now > syncPendingUntilMs)
        {
            syncPending = false;
            syncPendingUntilMs = 0L;

            if (config.debug())
            {
                log.info("[WOMSheetsBridge] Pending window expired; disarmed");
            }
            return;
        }

        final ChatMessageType type = event.getType();
        if (type != ChatMessageType.GAMEMESSAGE
                && type != ChatMessageType.CONSOLE
                && type != ChatMessageType.ENGINE
                && type != ChatMessageType.MESBOX)
        {
            return;
        }

        final String raw = event.getMessage();
        final String msg = raw == null ? "" : Text.removeTags(raw).trim();
        if (msg.isEmpty())
        {
            return;
        }

        if (config.debug() && msg.toLowerCase().startsWith("wom:"))
        {
            log.info("[WOMSheetsBridge] pending chat: type={} msg={}", type, msg);
        }

        // Check success match explicitly and log it
        final boolean success = looksLikeWomSyncSuccess(msg);
        final boolean failure = looksLikeWomSyncFailure(msg);

        if (config.debug() && msg.toLowerCase().startsWith("wom:"))
        {
            log.info("[WOMSheetsBridge] match check: success={} failure={}", success, failure);
        }

        if (failure)
        {
            syncPending = false;
            syncPendingUntilMs = 0L;

            if (config.debug())
            {
                log.info("[WOMSheetsBridge] Detected WOM failure; disarmed. msg={}", msg);
            }
            return;
        }

        if (!success)
        {
            return;
        }

        // Debounce
        if (now - lastTriggerAtMs < DEBOUNCE_MS)
        {
            if (config.debug())
            {
                log.info("[WOMSheetsBridge] Debounced success message ({}ms since last trigger)", now - lastTriggerAtMs);
            }
            return;
        }

        final String webAppUrl = config.webAppUrl();
        if (webAppUrl == null || webAppUrl.isBlank())
        {
            notifier.notify("WOM → Sheets Bridge: Configure Web App URL.");
            syncPending = false;
            syncPendingUntilMs = 0L;
            return;
        }

        syncPending = false;
        syncPendingUntilMs = 0L;
        lastTriggerAtMs = now;

        // enqueue() is async already; no executor needed here.
        triggerAppsScript(webAppUrl);
    }

    private static boolean looksLikeWomSyncSuccess(String message)
    {
        // Example completion line:
        // "WOM: Synced 494 clan members. 0 added, 0 removed, 0 ranks changed, 0 ranks ignored."
        final String lower = message.trim().toLowerCase();
        return lower.startsWith("wom:")
                && lower.contains("synced")
                && lower.contains("clan members");
    }

    private static boolean looksLikeWomSyncFailure(String message)
    {
        final String lower = message.trim().toLowerCase();
        return lower.startsWith("wom:")
                && (lower.contains("failed") || lower.contains("error"));
    }

    private void triggerAppsScript(String webAppUrl)
    {
        // no secret, minimal payload
        final String payload = "{}";

        try
        {
            // OkHttp 4+ prefers (String, MediaType). If your OkHttp is older, swap arg order.
            final RequestBody body = RequestBody.create(JSON, payload);

            final Request request = new Request.Builder()
                    .url(webAppUrl)
                    .post(body)
                    .build();

            if (config.debug())
            {
                log.info("[WOMSheetsBridge] HTTP POST (enqueue) -> {}", webAppUrl);
            }

            okHttpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    log.warn("[WOMSheetsBridge] Error calling Sheets endpoint", e);
                    notifier.notify("WOM → Sheets Bridge: Error calling Sheets endpoint.");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException
                {
                    try (Response r = response)
                    {
                        final int code = r.code();
                        final String responseBody = r.body() != null ? r.body().string() : "";

                        if (config.debug())
                        {
                            log.info("[WOMSheetsBridge] HTTP <- {} body={}", code, responseBody);
                        }

                        if (code < 200 || code >= 300)
                        {
                            notifier.notify("WOM → Sheets Bridge: Trigger failed (HTTP " + code + ")");
                            return;
                        }

                        notifier.notify("WOM → Sheets Bridge: Sheets script triggered.");
                    }
                }
            });
        }
        catch (Exception e)
        {
            log.warn("[WOMSheetsBridge] Error building/requesting Sheets endpoint", e);
            notifier.notify("WOM → Sheets Bridge: Error calling Sheets endpoint.");
        }
    }

    // Unused currently, but kept since it was in your file
    private static String escapeJson(String s)
    {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
