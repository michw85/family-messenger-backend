package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.CallSignalDto;
import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.service.ChatService;
import com.familymessenger.backend.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket-ретрансляция сигналов звонка (offer/answer/ICE/hangup) - только
 * для личных (DIRECT) чатов. Получатель всегда вычисляется из участников
 * комнаты в БД, а не берётся от клиента (как и в sendPrivateMessage).
 * WebSocket relay for call signals (offer/answer/ICE/hangup) - DIRECT chats
 * only. The recipient is always resolved from the room's participants in the
 * DB, never trusted from the client (same caution as sendPrivateMessage).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CallController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FcmService fcmService;

    // STOMP-сообщения (в отличие от обычных REST-запросов) не проходят через
    // open-session-in-view - без своей транзакции лениво загружаемая (по
    // умолчанию для @ManyToMany) коллекция participants недоступна к моменту
    // обращения к ней ниже.
    // STOMP messages (unlike regular REST requests) don't go through
    // open-session-in-view - without its own transaction, the lazily-loaded
    // (the @ManyToMany default) participants collection isn't available by
    // the time it's accessed below.
    @Transactional
    @MessageMapping("/call.signal/{roomId}")
    public void relaySignal(@Payload CallSignalDto signal,
                             @DestinationVariable String roomId,
                             Principal principal) {

        User sender = chatService.getUserByUsername(principal.getName());

        if (!chatService.isParticipant(roomId, sender.getId())) {
            log.warn("User {} tried to send a call signal to room {} without being a participant",
                    sender.getUsername(), roomId);
            throw new IllegalStateException("Not a participant of this chat room");
        }

        ChatRoom room = chatService.getChatRoomById(roomId);
        if (room.getType() != ChatRoom.RoomType.DIRECT) {
            throw new IllegalStateException("Calls are only supported in 1:1 (DIRECT) chats");
        }

        log.info("Room {} has {} participants: {}", roomId, room.getParticipants().size(),
                room.getParticipants().stream().map(User::getUsername).toList());

        User other = room.getParticipants().stream()
                .filter(p -> !p.getId().equals(sender.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No other participant in this room"));

        signal.setRoomId(roomId);
        signal.setFromUserId(sender.getId());
        signal.setFromUsername(sender.getUsername());
        signal.setTimestamp(System.currentTimeMillis());

        log.info("Relaying call signal {} from {} to {} (room {})",
                signal.getType(), sender.getUsername(), other.getUsername(), roomId);
        messagingTemplate.convertAndSendToUser(other.getUsername(), "/queue/call", signal);

        // Пуш только при OFFER, покрывает случай, когда собеседник свёрнул/закрыл
        // приложение - надёжного онлайн-статуса в системе нет (как и для обычных
        // сообщений), поэтому пуш отправляется безусловно, параллельно с STOMP.
        // Push only on OFFER, covers the callee having backgrounded/killed the
        // app - there's no reliable presence tracking (same as for regular
        // messages), so the push always fires in parallel with the STOMP signal.
        if (signal.getType() == CallSignalDto.CallSignalType.OFFER) {
            String token = other.getFcmToken();
            if (token != null && !token.isEmpty()) {
                fcmService.sendPushNotification(
                        token,
                        sender.getUsername(),
                        "📞 Incoming call",
                        Map.of(
                                "type", "incoming_call",
                                "callId", signal.getCallId(),
                                "roomId", roomId,
                                "roomName", room.getName(),
                                "callerUsername", sender.getUsername()
                        )
                );
            }
        }
    }
}
