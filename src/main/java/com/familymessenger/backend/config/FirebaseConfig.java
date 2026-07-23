package com.familymessenger.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Раньше ключ сервис-аккаунта Firebase Admin SDK лежал прямо в
 * src/main/resources и попадал в git/JAR в открытом виде - полноценный
 * приватный ключ с правами на отправку push-уведомлений кому угодно и
 * остальными правами, привязанными к этому сервис-аккаунту в GCP. Теперь
 * путь к файлу приходит из переменной окружения, а сам файл должен лежать
 * ВНЕ репозитория (примонтирован в контейнер только во время выполнения).
 *
 * Previously the Firebase Admin SDK service-account key sat right in
 * src/main/resources and ended up in git/the JAR in plaintext - a full
 * private key with rights to send push notifications to anyone plus
 * whatever other IAM roles are bound to that service account in GCP. The
 * file path now comes from an environment variable, and the file itself
 * must live OUTSIDE the repository (mounted into the container only at
 * runtime).
 */
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "firebase.credentials-path is required - set FIREBASE_CREDENTIALS_PATH to the "
                            + "(rotated) service account JSON file path, mounted outside the git repo/image");
        }

        try (InputStream in = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            return FirebaseApp.initializeApp(options);
        }
    }
}
