package com.creatorhub.core.identity.service;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(Throwable cause) {
        super("Email already registered", cause);
    }
}
