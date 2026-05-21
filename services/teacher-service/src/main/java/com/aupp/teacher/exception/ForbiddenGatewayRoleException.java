package com.aupp.teacher.exception;

public class ForbiddenGatewayRoleException extends RuntimeException {

    public ForbiddenGatewayRoleException(String expectedRole, String actualRole) {
        super("role '" + actualRole.trim() + "' is not permitted; expected " + expectedRole);
    }
}
