package com.englishhero.identity;

import java.util.List;

public record UserDto(String id, String username, String displayName, List<String> roles, String email, boolean emailVerified) {}
