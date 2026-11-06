package com.mangateca.api.identity.web;

import com.mangateca.api.identity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record UserResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(format = "email", requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role) {}
