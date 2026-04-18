package com.gaur.friendstogether.exception;

/**
 * ResourceNotFoundException: Jab DB mein koi data na mile (e.g. Invalid Room ID).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}