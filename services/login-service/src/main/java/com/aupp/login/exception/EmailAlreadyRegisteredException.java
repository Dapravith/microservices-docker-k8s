package com.aupp.login.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("email already registered: " + email);
    }
}
