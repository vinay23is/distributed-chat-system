package com.chat.platform.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 4000) String content,
        String messageType  // defaults to TEXT
) {}
