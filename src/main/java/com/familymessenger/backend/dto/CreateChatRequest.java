package com.familymessenger.backend.dto;

import com.familymessenger.backend.entity.ChatRoom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateChatRequest {
    @NotBlank(message = "Chat name is required")
    private String name;

    @NotNull(message = "Chat type is required")
    private ChatRoom.RoomType type;   // GROUP, FAMILY
}