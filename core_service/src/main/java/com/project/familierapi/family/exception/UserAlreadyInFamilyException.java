package com.project.familierapi.family.exception;

public class UserAlreadyInFamilyException extends RuntimeException {
    public UserAlreadyInFamilyException(String message) {
        super(message);
    }
}