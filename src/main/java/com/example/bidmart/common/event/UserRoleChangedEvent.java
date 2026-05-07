package com.example.bidmart.common.event;

import java.util.UUID;

public record UserRoleChangedEvent(
        UUID userId,
        String oldRole,
        String newRole
) {}
