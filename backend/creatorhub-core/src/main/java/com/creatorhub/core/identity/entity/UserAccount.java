package com.creatorhub.core.identity.entity;

public record UserAccount(long id, String emailNormalized, String passwordHash, String status) {
    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", status=" + status + "]";
    }
}
