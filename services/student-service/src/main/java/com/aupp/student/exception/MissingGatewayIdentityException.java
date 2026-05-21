package com.aupp.student.exception;

public class MissingGatewayIdentityException extends RuntimeException {

    public MissingGatewayIdentityException() {
        super("missing gateway identity header");
    }
}
