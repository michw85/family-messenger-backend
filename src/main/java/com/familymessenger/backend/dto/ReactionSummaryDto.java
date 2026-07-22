package com.familymessenger.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Сводка реакций одной эмодзи на сообщение: сколько человек её поставили и
 * их имена (это не персонализировано под конкретного зрителя, так что один и
 * тот же объект годится и для REST-ответа, и для WebSocket-рассылки всем -
 * "поставил ли реакцию я" клиент вычисляет сам, проверяя usernames)
 * Summary of one emoji's reactions on a message: how many people reacted
 * with it and their usernames (not personalized to a specific viewer, so the
 * same object works for both the REST response and the WebSocket broadcast
 * to everyone - the client derives "did I react" itself by checking usernames)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionSummaryDto {
    private String emoji;
    private int count;
    private List<String> usernames;
}
