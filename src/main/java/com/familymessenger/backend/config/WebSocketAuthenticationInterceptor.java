package com.familymessenger.backend.config;

import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.security.JwtTokenProvider;
import com.familymessenger.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Аутентификация и авторизация WebSocket/STOMP-соединений.
 *
 * Раньше при отсутствующем/невалидном JWT в CONNECT-фрейме соединение всё
 * равно устанавливалось (ошибка только логировалась) - то есть подключиться
 * по WebSocket мог кто угодно вообще без токена. Плюс не было никакой
 * проверки при SUBSCRIBE: даже если бы CONNECT требовал токен, любой
 * авторизованный (пусть даже как ДРУГОЙ пользователь) клиент мог подписаться
 * на /topic/room/{любой-id} и читать чужую переписку в реальном времени,
 * хотя сама отправка сообщений (chat.send) уже проверяла членство в чате.
 *
 * Authentication and authorization for WebSocket/STOMP connections.
 *
 * Previously, a missing/invalid JWT on the CONNECT frame still let the
 * connection through (the failure was only logged) - meaning anyone could
 * open a WebSocket connection with no token at all. On top of that, there
 * was no check at all on SUBSCRIBE: even if CONNECT required a token, any
 * authenticated client (as ANY user) could subscribe to
 * /topic/room/{any-id} and read someone else's conversation in real time,
 * even though sending messages (chat.send) already checked chat membership.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final ChatService chatService;

    /** Совпадает с /topic/room/{roomId} и его под-топиками (/typing, /read, /reactions) /
     * Matches /topic/room/{roomId} and its sub-topics (/typing, /read, /reactions) */
    private static final Pattern ROOM_TOPIC_PATTERN = Pattern.compile("^/topic/room/([^/]+)(?:/.*)?$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }

        return message;
    }

    /**
     * Требует валидный JWT для CONNECT. При его отсутствии/невалидности
     * бросает исключение - Spring переводит это в STOMP ERROR-фрейм и
     * закрывает соединение, а не просто логирует и пропускает дальше.
     * Requires a valid JWT for CONNECT. If missing/invalid, throws - Spring
     * turns this into a STOMP ERROR frame and closes the connection instead
     * of just logging and letting it through.
     */
    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("⚠️ Rejected WebSocket CONNECT: missing or malformed Authorization header");
            throw new MessagingException("Missing or invalid Authorization header");
        }

        token = token.substring(7);
        try {
            if (!tokenProvider.validateToken(token)) {
                log.warn("❌ Rejected WebSocket CONNECT: invalid JWT token");
                throw new MessagingException("Invalid JWT token");
            }
            String username = tokenProvider.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            accessor.setUser(auth);
            log.info("✅ WebSocket authenticated user: {}", username);
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Rejected WebSocket CONNECT: authentication failed", e);
            throw new MessagingException("WebSocket authentication failed", e);
        }
    }

    /**
     * Проверяет, что пользователь, установивший это соединение, состоит в
     * чате, на топик которого он пытается подписаться - иначе подписка на
     * /topic/room/{roomId} позволила бы читать чужую переписку в реальном
     * времени в обход проверок членства, которые есть только на отправке.
     * Verifies that the user who established this connection is a
     * participant of the chat whose topic they're trying to subscribe to -
     * otherwise subscribing to /topic/room/{roomId} would let anyone read
     * someone else's conversation live, bypassing the membership checks
     * that only exist on the send side.
     */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = ROOM_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            // Не топик конкретной комнаты чата (например, /user/queue/private) - не наша забота здесь
            // Not a specific chat-room topic (e.g. /user/queue/private) - not our concern here
            return;
        }

        String roomId = matcher.group(1);
        Principal principal = accessor.getUser();
        Object userDetails = (principal instanceof UsernamePasswordAuthenticationToken auth) ? auth.getPrincipal() : null;

        if (!(userDetails instanceof User user)) {
            log.warn("❌ Rejected SUBSCRIBE to {}: no authenticated user on this connection", destination);
            throw new MessagingException("Not authenticated");
        }

        if (!chatService.isParticipant(roomId, user.getId())) {
            log.warn("❌ User {} tried to subscribe to room {} without being a participant", user.getUsername(), roomId);
            throw new MessagingException("Not a participant of this chat room");
        }
    }
}
