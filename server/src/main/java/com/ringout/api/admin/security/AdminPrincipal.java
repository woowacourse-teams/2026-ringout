package com.ringout.api.admin.security;

import java.io.Serializable;

public record AdminPrincipal(Long id, String email, String name) implements Serializable { }
