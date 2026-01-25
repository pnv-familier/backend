package com.project.familierapi.shared.exception;


import com.project.familierapi.shared.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.logging.Level;
import java.util.logging.Logger;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        Logger.getLogger(GlobalExceptionHandler.class.getName()).log(Level.SEVERE, null, ex);
        return new ResponseEntity<>(
                new ErrorResponse("Unexpected error occurs", null, null),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
