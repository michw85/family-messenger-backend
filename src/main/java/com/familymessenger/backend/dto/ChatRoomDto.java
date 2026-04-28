package com.familymessenger.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.familymessenger.backend.entity.ChatRoom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDto {

    private String id;                          // Уникальный идентификатор комнаты / Unique room identifier
    private String name;                        // Название комнаты / Room name
    private ChatRoom.RoomType type;             // Тип комнаты: DIRECT/GROUP/FAMILY / Room type
    private Long createdBy;                     // ID создателя комнаты / Creator user ID
    private List<UserDto> participants;         // Список участников / List of participants
    private LocalDateTime createdAt;            // Дата создания / Creation date
    private LocalDateTime updatedAt;            // Дата обновления / Update date

    /**
     * Конвертирует Entity в DTO
     * Converts Entity to DTO
     *
     * @param chatRoom - сущность чата / chat entity
     * @return DTO чата для передачи клиенту / chat DTO for client
     */
    public static ChatRoomDto fromEntity(ChatRoom chatRoom) {
        // Проверка на null, чтобы избежать NullPointerException
        // Null check to avoid NullPointerException
        if (chatRoom == null) {
            return null;
        }

        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .name(chatRoom.getName())
                .type(chatRoom.getType())
                // Тернарный оператор: если создатель есть - берём его ID, иначе null
                // Ternary operator: if creator exists - get ID, otherwise null
                .createdBy(chatRoom.getCreatedBy() != null ? chatRoom.getCreatedBy().getId() : null)
                // Тернарный оператор: если участники есть - преобразуем список, иначе null
                // Ternary operator: if participants exist - convert list, otherwise null
                .participants(chatRoom.getParticipants() != null ?
                        chatRoom.getParticipants().stream()
                        .map(UserDto::fromEntity)      // Преобразуем каждого User в UserDto
                        .collect(Collectors.toList()) : null)
                .createdAt(chatRoom.getCreatedAt())
                .updatedAt(chatRoom.getUpdatedAt())
                .build();
    }
}