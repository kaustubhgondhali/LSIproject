package com.lordsai.lsi.security;

import com.lordsai.lsi.entity.enums.Role;

/** The authenticated principal placed in the SecurityContext by the JWT filter. */
public record AuthUser(Long id, String email, String fullName, Role role, Long sessionId) {

    public boolean is(Role expected) {
        return role == expected;
    }
}
