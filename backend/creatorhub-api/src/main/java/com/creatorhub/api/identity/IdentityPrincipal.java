package com.creatorhub.api.identity;

import java.io.Serializable;

public record IdentityPrincipal(long userId) implements Serializable {}
