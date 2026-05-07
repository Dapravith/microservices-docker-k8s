package com.aupp.teacher.exception;

public class MissingCallerIdentityException extends RuntimeException {
    public MissingCallerIdentityException(String message) {
        super(message);
    }
}
