package com.campus.repair.security;

public enum UserRole {
    STUDENT, WORKER, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
