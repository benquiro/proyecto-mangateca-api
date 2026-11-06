package com.mangateca.api.identity.web;

import com.mangateca.api.identity.Role;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, Role role) {}
