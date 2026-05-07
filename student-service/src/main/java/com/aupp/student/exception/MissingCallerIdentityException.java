package com.aupp.student.exception;

public class MissingCallerIdentityException extends RuntimeException {
    public MissingCallerIdentityException(String message) {
        super(message);
    }
}
