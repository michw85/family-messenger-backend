package com.familymessenger.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Сервис отправки email через Gmail API (используется для одноразовых кодов
 * входа - 2FA). Письмо реально уходит с серверов Google от имени настоящего
 * аккаунта (через OAuth2 refresh-токен), поэтому доставляется так же надёжно,
 * как обычное письмо из Gmail - в отличие от сторонних ESP (Resend/Brevo),
 * которые не могут подписать письмо правильным DKIM/DMARC для чужого домена
 * gmail.com и из-за этого иногда попадают в задержку/спам.
 * Email sending service via the Gmail API (used for one-time login codes -
 * 2FA). The message is actually sent from Google's own servers on behalf of
 * the real account (via an OAuth2 refresh token), so it's delivered as
 * reliably as a normal Gmail-sent email - unlike third-party ESPs
 * (Resend/Brevo), which can't sign the message with proper DKIM/DMARC for
 * someone else's gmail.com domain and occasionally get deferred/spam-filtered.
 */
@Slf4j
@Service
public class EmailService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final RestTemplate restTemplate;

    @Value("${gmail.client-id}")
    private String clientId;

    @Value("${gmail.client-secret}")
    private String clientSecret;

    @Value("${gmail.refresh-token}")
    private String refreshToken;

    @Value("${gmail.sender-email}")
    private String senderEmail;

    public EmailService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Обменивает refresh-токен на короткоживущий access-токен
     * Exchanges the refresh token for a short-lived access token
     */
    private String fetchAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URL, new HttpEntity<>(form, headers), Map.class);
        Object token = response.getBody() != null ? response.getBody().get("access_token") : null;
        if (token == null) {
            throw new IllegalStateException("Gmail API: no access_token in OAuth response");
        }
        return token.toString();
    }

    public void sendOtpEmail(String to, String code) {
        String accessToken = fetchAccessToken();

        String subject = "Bonds — код подтверждения входа / Login verification code";
        String body =
                "Ваш код подтверждения: " + code + "\n" +
                        "Код действителен 15 минут. Если это были не вы, проигнорируйте это письмо.\n\n" +
                        "Your verification code: " + code + "\n" +
                        "The code is valid for 15 minutes. If this wasn't you, ignore this email.";

        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8))
                + "?=";

        String rawMessage =
                "From: Bonds <" + senderEmail + ">\r\n" +
                        "To: " + to + "\r\n" +
                        "Subject: " + encodedSubject + "\r\n" +
                        "MIME-Version: 1.0\r\n" +
                        "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                        "Content-Transfer-Encoding: base64\r\n\r\n" +
                        Base64.getMimeEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));

        String encodedRaw = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawMessage.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        restTemplate.postForEntity(SEND_URL, new HttpEntity<>(Map.of("raw", encodedRaw), headers), String.class);
        log.info("OTP email sent via Gmail API to: {}", to);
    }
}
