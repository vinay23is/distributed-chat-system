package com.chat.platform.dto.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(min = 1, max = 80) String name,
        @Size(max = 300) String description,
        String type  // PUBLIC or PRIVATE, defaults to PUBLIC
) {}
