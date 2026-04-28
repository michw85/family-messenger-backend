package com.familymessenger.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.familymessenger.backend.entity.User;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private User.UserStatus status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    // Конвертация из Entity в DTO
    public static UserDto fromEntity(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .lastSeen(user.getLastSeen())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // Конвертация из DTO в Entity
    public User toEntity() {
        User user = new User();
        user.setId(this.id);
        user.setUsername(this.username);
        user.setEmail(this.email);
        user.setAvatarUrl(this.avatarUrl);
        user.setStatus(this.status);
        user.setLastSeen(this.lastSeen);
        user.setCreatedAt(this.createdAt);
        return user;
    }
}