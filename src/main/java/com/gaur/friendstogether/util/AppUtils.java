package com.gaur.friendstogether.util;

import java.security.SecureRandom;

/**
 * AppUtils: Helper class for common utilities.
 */
public class AppUtils {

    private static final SecureRandom random = new SecureRandom();

    /**
     * generateRoomId: 8-digit ka unique numeric ID generate karta hai.
     * Range: 10,000,000 to 99,999,999
     * Example: 58291034
     */
    public static String generateRoomId() {
        // Pehla digit 1-9 ke beech (taaki 8-digit length maintain rahe)
        int firstDigit = 1 + random.nextInt(9);

        // Baaki 7 digits (0 to 9,999,999)
        int remainingDigits = random.nextInt(10000000);

        // Combine karke String return karega
        return String.valueOf(firstDigit * 10000000L + remainingDigits);
    }
}