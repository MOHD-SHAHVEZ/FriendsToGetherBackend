package com.gaur.friendstogether.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================================
 * Razorpay Configuration
 * ============================================================================
 *
 * Provides the RazorpayClient bean globally.
 */
@Configuration
public class RazorpayConfig {

    private static final Logger logger = LoggerFactory.getLogger(RazorpayConfig.class);

    @Value("${razorpay.api.key}")
    private String key;

    @Value("${razorpay.api.secret}")
    private String secret;

    @Bean
    public RazorpayClient razorpayClient() {
        try {
            logger.info("[RAZORPAY] Initializing Razorpay Client with Key: {}", key);
            return new RazorpayClient(key, secret);
        } catch (RazorpayException e) {
            logger.error("[RAZORPAY] Failed to initialize Razorpay Client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Razorpay Client", e);
        }
    }
}
