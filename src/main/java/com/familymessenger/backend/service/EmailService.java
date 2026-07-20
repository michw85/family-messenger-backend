package com.familymessenger.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Сервис отправки email (используется для одноразовых кодов входа - 2FA)
 * Email sending service (used for one-time login codes - 2FA)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendOtpEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Bonds — код подтверждения входа / Login verification code");
        message.setText(
                "Ваш код подтверждения: " + code + "\n" +
                "Код действителен 5 минут. Если это были не вы, проигнорируйте это письмо.\n\n" +
                "Your verification code: " + code + "\n" +
                "The code is valid for 5 minutes. If this wasn't you, ignore this email."
        );
        mailSender.send(message);
        log.info("OTP email sent to: {}", to);
    }
}
