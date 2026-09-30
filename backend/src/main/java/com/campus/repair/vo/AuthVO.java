package com.campus.repair.vo;

import com.campus.repair.security.UserRole;
import java.time.Instant;

public record AuthVO(String token, Instant expiresAt, UserVO user, UserRole role) {}
