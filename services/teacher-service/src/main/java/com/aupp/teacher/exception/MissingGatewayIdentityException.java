package com.aupp.teacher.exception;

public class MissingGatewayIdentityException extends RuntimeException {

    public MissingGatewayIdentityException() {
        super("missing gateway identity header");
    }
}
