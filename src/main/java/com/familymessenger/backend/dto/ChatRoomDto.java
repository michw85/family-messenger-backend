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
    private String id;
    private String name;
    private ChatRoom.RoomType type;
    private Long createdBy;
    private List<UserDto> participants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Конвертация из Entity в DTO
    public static ChatRoomDto fromEntity(ChatRoom chatRoom) {
        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .name(chatRoom.getName())
                .type(chatRoom.getType())
                .createdBy(chatRoom.getCreatedBy() != null ? chatRoom.getCreatedBy().getId() : null)
                .participants(chatRoom.getParticipants() != null ?
                        chatRoom.getParticipants().stream()
                        .map(UserDto::fromEntity)
                        .collect(Collectors.toList()) : null)
                .createdAt(chatRoom.getCreatedAt())
                .updatedAt(chatRoom.getUpdatedAt())
                .build();
    }
}