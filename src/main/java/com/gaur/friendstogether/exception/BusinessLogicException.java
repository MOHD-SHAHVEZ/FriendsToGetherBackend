package com.gaur.friendstogether.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

/**
 * BusinessLogicException: Logic errors ke liye (e.g. Room Full, Insufficient Coins).
 */
@Getter
public class BusinessLogicException extends RuntimeException {

    private final HttpStatus status;

    public BusinessLogicException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}