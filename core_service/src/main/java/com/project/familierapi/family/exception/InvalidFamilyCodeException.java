package com.project.familierapi.family.exception;

public class InvalidFamilyCodeException extends RuntimeException {
    public InvalidFamilyCodeException(String message) {
        super(message);
    }
}