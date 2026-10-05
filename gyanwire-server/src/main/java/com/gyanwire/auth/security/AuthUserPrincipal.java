package com.gyanwire.auth.security;

import java.util.UUID;

public record AuthUserPrincipal(UUID id, String email, String role) {}
