package com.familymessenger.backend.dto;

import lombok.Data;

/**
 * Запрос на запись итога звонка в историю чата (POST /api/calls/{roomId}/log).
 * Request to log a call's outcome into chat history (POST /api/calls/{roomId}/log).
 */
@Data
public class CallLogRequest {
    private String callId;
    private String result; // ANSWERED | DECLINED | MISSED | CANCELLED
    private Integer durationSeconds; // только для ANSWERED / only meaningful for ANSWERED
}
