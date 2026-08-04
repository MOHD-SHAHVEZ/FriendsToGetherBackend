package com.gaur.friendstogether.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentInitiateRequest {
    
    @NotBlank(message = "Order Number is required")
    private String orderNumber;

    @NotBlank(message = "Idempotency Key is required")
    private String idempotencyKey;
}
