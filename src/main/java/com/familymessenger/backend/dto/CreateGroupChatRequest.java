package com.familymessenger.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * DTO для создания группового чата
 * DTO for creating group chat
 */
@Data
public class CreateGroupChatRequest {

    @NotBlank(message = "Chat name is required")
    private String name;

    @NotNull(message = "At least one participant required")
    private List<Long> participantIds; // ID участников (не включая создателя) / participant IDs (excluding creator)
}