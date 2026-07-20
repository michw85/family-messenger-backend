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
 * Сервис отправки email через HTTP API Brevo (используется для одноразовых
 * кодов входа - 2FA). Отправка идёт по HTTPS (порт 443), а не по SMTP,
 * потому что облачные провайдеры (в т.ч. DigitalOcean) по умолчанию блокируют
 * исходящий SMTP-трафик на дроплетах. В отличие от Resend без подтверждённого
 * домена, Brevo с подтверждённым отправителем (email, без DNS) шлёт письма на
 * любые адреса получателей, а не только на адрес аккаунта.
 * Email sending service via the Brevo HTTP API (used for one-time login
 * codes - 2FA). Sends over HTTPS (port 443) instead of SMTP, because cloud
 * providers (including DigitalOcean) block outbound SMTP on droplets by
 * default. Unlike Resend without a verified domain, Brevo with a verified
 * sender (just an email address, no DNS) delivers to any recipient, not only
 * the account's own address.
 */
@Slf4j
@Service
public class EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.from-address}")
    private String fromAddress;

    @Value("${brevo.from-name}")
    private String fromName;

    public EmailService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendOtpEmail(String to, String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", fromName, "email", fromAddress),
                "to", List.of(Map.of("email", to)),
                "subject", "Bonds — код подтверждения входа / Login verification code",
                "textContent",
                "Ваш код подтверждения: " + code + "\n" +
                        "Код действителен 5 минут. Если это были не вы, проигнорируйте это письмо.\n\n" +
                        "Your verification code: " + code + "\n" +
                        "The code is valid for 5 minutes. If this wasn't you, ignore this email."
        );

        restTemplate.postForEntity(BREVO_API_URL, new HttpEntity<>(body, headers), String.class);
        log.info("OTP email sent via Brevo to: {}", to);
    }
}
