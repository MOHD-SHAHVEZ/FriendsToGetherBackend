package com.gaur.friendstogether.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaymentInitiateResponse {
    
    private String razorpayOrderId;
    private BigDecimal amount;
    private String currency;
    private String keyId; // Sent to frontend so it doesn't have to hardcode the key
    private String internalPaymentReference;
}
