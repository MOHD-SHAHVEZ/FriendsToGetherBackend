package com.gaur.friendstogether.exception;

/**
 * WalletNotFoundException
 *
 * Jab user ke liye wallet exist nahi karta.
 *
 * HTTP Status: 404 Not Found
 *
 * Example:
 * New user jisne kabhi koi transaction nahi kiya,
 * uska wallet abhi create nahi hua.
 */
public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String message) {
        super(message);
    }
}
