package com.example.ordermanagement.security;

import java.util.UUID;

public record CurrentUser(UUID id, AppRole role) {
}
