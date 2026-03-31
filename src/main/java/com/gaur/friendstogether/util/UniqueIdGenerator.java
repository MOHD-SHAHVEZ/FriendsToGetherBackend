package com.gaur.friendstogether.util;

import com.gaur.friendstogether.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class UniqueIdGenerator {

    private final UserRepository userRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateUniqueUserId() {
        String newId;
        boolean isUnique;
        do {
            // 9-digit random number (100,000,000 to 999,999,999)
            long number = 100_000_000L + (long) (RANDOM.nextDouble() * 900_000_000L);
            newId = String.valueOf(number);

            // Check karo ki DB mein pehle se toh nahi hai
            isUnique = !userRepository.existsByUserId(newId);
        } while (!isUnique);

        return newId;
    }
}