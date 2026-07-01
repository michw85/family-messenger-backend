package com.familymessenger.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class FcmService {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendPushNotification(String deviceToken, String title, String body) {
        try {
            /*Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(notification)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent: {}", response);*/

            String json = String.format("""
                {
                  "to": "%s",
                  "title": "%s",
                  "body": "%s",
                  "sound": "default",
                  "priority": "high"
                }
                """, deviceToken,
                    title.replace("\"", "\\\""),
                    body.replace("\"", "\\\""));

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