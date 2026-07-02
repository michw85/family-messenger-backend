package com.familymessenger.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Конфигурация WebSocket для реального времени
 * WebSocket configuration for real-time messaging
 */
@Configuration
@EnableWebSocketMessageBroker  // Включаем поддержку WebSocket с STOMP протоколом
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthenticationInterceptor authenticationInterceptor;

    /**
     * Регистрация WebSocket endpoint'а (точки подключения)
     * Register WebSocket endpoint (connection point)
     *
     * @param registry - регистратор endpoint'ов / endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Добавляем endpoint /ws, к которому будут подключаться клиенты
        // Add /ws endpoint for client connections
        System.out.println("🔌 Registering WebSocket endpoint /ws");
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Разрешаем подключения с любых доменов (для разработки)
                .withSockJS()                   // Включаем SockJS (fallback для браузеров без WebSocket)
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
    }

    /**
     * Настройка брокера сообщений (куда и как отправлять сообщения)
     * Configure message broker (where and how to send messages)
     *
     * @param config - регистратор брокера / broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Префикс для топиков (куда клиенты подписываются для получения сообщений)
        // Topic prefix (where clients subscribe to receive messages)
        config.enableSimpleBroker("/topic", "/queue")

        // Префикс для отправки сообщений от клиента (куда клиенты отправляют)
        // App prefix (where clients send messages)
        config.setApplicationDestinationPrefixes("/app");

        // Префикс для личных сообщений (отправка конкретному пользователю)
        // User prefix for private messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}