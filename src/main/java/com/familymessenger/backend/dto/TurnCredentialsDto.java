package com.familymessenger.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Временные учётные данные для coturn (TURN REST API, use-auth-secret) -
 * секрет никогда не покидает бэкенд, клиент получает только короткоживущие
 * username/credential.
 * Short-lived credentials for coturn (TURN REST API, use-auth-secret) - the
 * shared secret never leaves the backend, the client only gets a short-lived
 * username/credential pair.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnCredentialsDto {
    private String username;
    private String credential;
    private long ttlSeconds;
    private List<String> urls;
}
