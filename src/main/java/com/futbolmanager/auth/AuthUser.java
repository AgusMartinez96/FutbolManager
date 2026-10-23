package com.futbolmanager.auth;

import java.io.Serializable;
import java.util.UUID;

public record AuthUser(UUID id, String username, String displayName) implements Serializable {}
