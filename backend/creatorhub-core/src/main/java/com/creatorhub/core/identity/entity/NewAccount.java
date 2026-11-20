package com.creatorhub.core.identity.entity;

public class NewAccount {
    private Long id;
    private final String emailNormalized;
    private final String passwordHash;

    public NewAccount(String emailNormalized, String passwordHash) {
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
