package com.gaur.friendstogether.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ChatUtils {

    // --- [PROFESSIONAL LOGIC]: Unique Room ID based on sorted User IDs ---
    public static String generateRoomId(String userId1, String userId2) {
        return Arrays.asList(userId1, userId2)
                .stream()
                .sorted()
                .collect(Collectors.joining("_"));
    }
}