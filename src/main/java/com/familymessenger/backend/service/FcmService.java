package com.familymessenger.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class FcmService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Отправка push-уведомления без дополнительных данных (обратная совместимость)
     * Send a push notification without extra data (backward compatibility)
     */
    public void sendPushNotification(String deviceToken, String title, String body) {
        sendPushNotification(deviceToken, title, body, null);
    }

    /**
     * Отправка push-уведомления с данными (например, roomId/roomName), чтобы
     * при нажатии на уведомление приложение открыло нужный чат, а не просто
     * стартовало с главного экрана.
     * Send a push notification with data (e.g. roomId/roomName) so tapping the
     * notification opens the right chat instead of just launching the app.
     */
    public void sendPushNotification(String deviceToken, String title, String body, Map<String, String> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("to", deviceToken);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("sound", "default");
            payload.put("priority", "high");
            // Должен совпадать с id канала, создаваемого в
            // notifications.ts::registerForPushNotificationsAsync - без этого
            // Expo целится в канал с буквальным именем "default", а не в тот,
            // что реально зарегистрирован на устройстве.
            // Must match the channel id created in
            // notifications.ts::registerForPushNotificationsAsync - without
            // this Expo targets a channel literally named "default" instead
            // of the one actually registered on the device.
            payload.put("channelId", "default-v2");
            if (data != null && !data.isEmpty()) {
                payload.put("data", data);
            }

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://exp.host/--/api/v2/push/send"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            log.info("Expo push notification sent: {}", response.body());
        } catch (Exception e) {
            log.error("Failed to send push notification to {}: {}", deviceToken, e.getMessage());
        }
    }
}
