package com.familymessenger.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Сервис отправки email через HTTP API Resend (используется для одноразовых
 * кодов входа - 2FA). Отправка идёт по HTTPS (порт 443), а не по SMTP,
 * потому что облачные провайдеры (в т.ч. DigitalOcean) по умолчанию блокируют
 * исходящий SMTP-трафик на дроплетах.
 * Email sending service via the Resend HTTP API (used for one-time login
 * codes - 2FA). Sends over HTTPS (port 443) instead of SMTP, because cloud
 * providers (including DigitalOcean) block outbound SMTP on droplets by default.
 */
@Slf4j
@Service
public class EmailService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-address}")
    private String fromAddress;

    public EmailService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendOtpEmail(String to, String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "from", fromAddress,
                "to", List.of(to),
                "subject", "Bonds — код подтверждения входа / Login verification code",
                "text",
                "Ваш код подтверждения: " + code + "\n" +
                        "Код действителен 5 минут. Если это были не вы, проигнорируйте это письмо.\n\n" +
                        "Your verification code: " + code + "\n" +
                        "The code is valid for 5 minutes. If this wasn't you, ignore this email."
        );

        restTemplate.postForEntity(RESEND_API_URL, new HttpEntity<>(body, headers), String.class);
        log.info("OTP email sent via Resend to: {}", to);
    }
}
