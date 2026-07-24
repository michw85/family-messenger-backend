package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.CallLogRequest;
import com.familymessenger.backend.dto.ChatMessageDto;
import com.familymessenger.backend.dto.TurnCredentialsDto;
import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.service.ChatService;
import com.familymessenger.backend.service.FcmService;
import com.familymessenger.backend.service.TurnCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST-эндпоинты для звонков: выдача временных TURN-креденшлов и запись
 * итога звонка в историю чата (обычным Message-сообщением).
 * REST endpoints for calls: minting short-lived TURN credentials and logging
 * a call's outcome into chat history (as a regular Message).
 */
@Slf4j
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallRestController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FcmService fcmService;
    private final TurnCredentialService turnCredentialService;

    /**
     * Временные TURN-креды - не привязаны к конкретной комнате, ограничение
     * "только DIRECT-чаты" проверяется на уровне сигналинга (CallController).
     * Short-lived TURN credentials - not room-scoped, the "DIRECT chats only"
     * restriction is enforced at the signaling layer (CallController).
     */
    @GetMapping("/turn-credentials")
    public ResponseEntity<TurnCredentialsDto> getTurnCredentials(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(turnCredentialService.generate(user.getUsername()));
    }

    /**
     * Запись итога звонка в историю чата (пропущен/отклонён/отвечен/отменён).
     * Log a call's outcome into chat history (missed/declined/answered/cancelled).
     */
    @PostMapping("/{roomId}/log")
    public ResponseEntity<?> logCall(@PathVariable String roomId,
                                      @RequestBody CallLogRequest request,
                                      @AuthenticationPrincipal User user) {
        if (!chatService.isParticipant(roomId, user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Not a participant of this chat / Вы не участник этого чата");
        }

        ChatRoom room = chatService.getChatRoomById(roomId);
        if (room.getType() != ChatRoom.RoomType.DIRECT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Calls are only supported in 1:1 (DIRECT) chats");
        }

        Message.MessageType type = switch (request.getResult()) {
            case "ANSWERED" -> Message.MessageType.CALL_ANSWERED;
            case "DECLINED" -> Message.MessageType.CALL_DECLINED;
            case "MISSED" -> Message.MessageType.CALL_MISSED;
            case "CANCELLED" -> Message.MessageType.CALL_CANCELLED;
            default -> throw new IllegalArgumentException("Unknown call result: " + request.getResult());
        };

        String content = type == Message.MessageType.CALL_ANSWERED
                ? "{\"durationSeconds\":" + (request.getDurationSeconds() != null ? request.getDurationSeconds() : 0) + "}"
                : null;

        Message saved = chatService.saveMessage(content, roomId, user, type, null, null, null);
        ChatMessageDto dto = ChatMessageDto.fromEntity(saved);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, dto);

        if ("MISSED".equals(request.getResult())) {
            List<String> tokens = chatService.getParticipantFcmTokens(roomId, user.getId());
            for (String token : tokens) {
                fcmService.sendPushNotification(
                        token,
                        user.getUsername(),
                        "📞 Missed call",
                        Map.of("roomId", roomId, "roomName", room.getName())
                );
            }
        }

        return ResponseEntity.ok(dto);
    }
}
