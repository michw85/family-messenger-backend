package com.familymessenger.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сигнал звонка (offer/answer/ICE-кандидат/управление) - пересылается через
 * персональную STOMP-очередь получателя (/user/queue/call), не сохраняется в БД.
 * A call signal (offer/answer/ICE candidate/control) - relayed through the
 * recipient's personal STOMP queue (/user/queue/call), never persisted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignalDto {

    private String callId;
    private CallSignalType type;

    private String sdp;             // OFFER / ANSWER
    private String candidate;       // ICE_CANDIDATE
    private String sdpMid;          // ICE_CANDIDATE
    private Integer sdpMLineIndex;  // ICE_CANDIDATE

    private String roomId;          // проставляется сервером / server-stamped
    private Long fromUserId;        // проставляется сервером / server-stamped
    private String fromUsername;    // проставляется сервером / server-stamped
    private Long timestamp;         // проставляется сервером / server-stamped

    public enum CallSignalType {
        OFFER, ANSWER, ICE_CANDIDATE, ACCEPT, DECLINE, CANCEL, HANGUP, BUSY
    }
}
