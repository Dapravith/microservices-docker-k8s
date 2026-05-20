package com.aupp.gateway.security;

public class InvalidJwtException extends RuntimeException {

    public InvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
