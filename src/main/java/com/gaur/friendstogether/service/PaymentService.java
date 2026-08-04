package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.request.PaymentInitiateRequest;
import com.gaur.friendstogether.dto.request.PaymentVerifyRequest;
import com.gaur.friendstogether.dto.response.PaymentInitiateResponse;
import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.exception.BusinessLogicException;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.CoinOrderRepository;
import com.gaur.friendstogether.repository.PaymentRepository;
import com.gaur.friendstogether.repository.PaymentWebhookLogRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final CoinOrderRepository coinOrderRepository;
    private final WalletService walletService;
    private final IdempotencyService idempotencyService;
    private final RazorpayClient razorpayClient;
    private final PaymentWebhookLogRepository webhookLogRepository;

    @Value("${razorpay.api.key}")
    private String razorpayKeyId;

    @Value("${razorpay.api.secret}")
    private String razorpaySecret;
    
    @Value("${razorpay.webhook.secret}")
    private String razorpayWebhookSecret;

    /**
     * INITIATE PAYMENT (Backend creates Razorpay Order)
     */
    @Transactional
    public PaymentInitiateResponse initiatePayment(Long userId, PaymentInitiateRequest request) {

        logger.info("[PAYMENT] Initiating payment for orderNumber={}", request.getOrderNumber());

        // 1. Check Idempotency (Prevent double clicks)
        String httpMethod = "POST";
        String requestPath = "/api/payments/initiate";
        
        Optional<IdempotencyRecord> existingIdempotency = idempotencyService
                .checkAndReturn(request.getIdempotencyKey(), httpMethod, requestPath);
                
        if (existingIdempotency.isPresent()) {
            logger.info("[PAYMENT] Returning cached payment response for key={}", request.getIdempotencyKey());
            IdempotencyRecord record = existingIdempotency.get();
            // Since it's a cached response, the user already has a Razorpay session
            Payment existingPayment = paymentRepository.findById(record.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment record missing for idempotency key"));
            
            return PaymentInitiateResponse.builder()
                    .razorpayOrderId(existingPayment.getGatewayOrderId())
                    .amount(existingPayment.getAmount())
                    .currency(existingPayment.getCurrency().name())
                    .keyId(razorpayKeyId)
                    .internalPaymentReference(existingPayment.getPaymentReference())
                    .build();
        }

        // 2. Find and Validate Internal Order
        CoinOrder order = coinOrderRepository.findByOrderNumber(request.getOrderNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderNumber()));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessLogicException("Not authorized to pay for this order", HttpStatus.FORBIDDEN);
        }

        if (order.getStatus() != CoinOrderStatus.CREATED 
                && order.getStatus() != CoinOrderStatus.PAYMENT_FAILED
                && order.getStatus() != CoinOrderStatus.PAYMENT_PENDING) {
            throw new BusinessLogicException("Order cannot be paid in current status: " + order.getStatus(), HttpStatus.BAD_REQUEST);
        }

        // 3. Create Razorpay Order
        Order razorpayOrder;
        try {
            JSONObject orderRequest = new JSONObject();
            // Razorpay amount is in paise (Multiply INR by 100)
            int amountInPaise = order.getOrderAmount().multiply(new BigDecimal("100")).intValue();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + UUID.randomUUID().toString().substring(0, 8));

            razorpayOrder = razorpayClient.orders.create(orderRequest);
            logger.info("[PAYMENT] Razorpay order created successfully", razorpayOrder);
        } catch (RazorpayException e) {
            logger.error("[PAYMENT] Razorpay order creation failed", e);
            throw new BusinessLogicException("Payment gateway error: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }

        String gatewayOrderId = razorpayOrder.get("id");

        // 4. Create Internal Payment Record
        Payment payment = Payment.builder()
                .paymentReference("PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                .order(order)
                .provider(PaymentProvider.RAZORPAY)
                .gatewayOrderId(gatewayOrderId)
                .amount(order.getOrderAmount())
                .currency(CurrencyType.INR)
                .status(PaymentStatus.PENDING)
                .verificationStatus(PaymentVerificationStatus.PENDING)
                .walletCredited(false)
                .retryCount(0)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // 5. Update Order Status
        order.setStatus(CoinOrderStatus.PAYMENT_PENDING);
        coinOrderRepository.save(order);

        // 6. Save Idempotency Record
        idempotencyService.createPaymentRecord(
                request.getIdempotencyKey(),
                httpMethod,
                requestPath,
                savedPayment.getId(),
                order.getUser(),
                "dummy_hash_for_payment", // Since request body only has order number
                "{}", // No need to cache complex JSON, we rebuild response above
                201,
                gatewayOrderId,
                null // No checkout URL for standard Razorpay
        );

        return PaymentInitiateResponse.builder()
                .razorpayOrderId(gatewayOrderId)
                .amount(order.getOrderAmount())
                .currency("INR")
                .keyId(razorpayKeyId)
                .internalPaymentReference(savedPayment.getPaymentReference())
                .build();
    }

    /**
     * VERIFY PAYMENT (Frontend calls this after Razorpay Popup Success)
     */
    @Transactional
    public void verifyPayment(Long userId, PaymentVerifyRequest request) {
        
        logger.info("[PAYMENT] Verifying payment for Razorpay Order={}", request.getRazorpayOrderId());

        // 1. Verify Signature locally
        try {
            String payload = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();
            boolean isValid = Utils.verifySignature(payload, request.getRazorpaySignature(), razorpaySecret);
            
            if (!isValid) {
                logger.warn("[PAYMENT] INVALID SIGNATURE for Order={}", request.getRazorpayOrderId());
                throw new BusinessLogicException("Invalid payment signature. Potential tampering detected.", HttpStatus.BAD_REQUEST);
            }
        } catch (RazorpayException e) {
            logger.error("[PAYMENT] Signature verification error", e);
            throw new BusinessLogicException("Signature verification failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 2. Fetch Payment Record
        Payment payment = paymentRepository.findByGatewayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for gateway order"));

        if (!payment.getOrder().getUser().getId().equals(userId)) {
            throw new BusinessLogicException("Not authorized", HttpStatus.FORBIDDEN);
        }

        // 3. Double-Spend Protection
        if (payment.isWalletCredited()) {
            logger.info("[PAYMENT] Wallet already credited for this payment. Skipping.");
            return; // Success, do nothing
        }

        // 4. Update Payment Record
        payment.setGatewayPaymentId(request.getRazorpayPaymentId());
        payment.setGatewaySignature(request.getRazorpaySignature());
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setVerificationStatus(PaymentVerificationStatus.VERIFIED);
        payment.setWalletCredited(true);
        payment.setCompletedAt(java.time.LocalDateTime.now());
        
        paymentRepository.save(payment);

        // 5. Update CoinOrder Record
        CoinOrder order = payment.getOrder();
        order.setStatus(CoinOrderStatus.PAYMENT_SUCCESS);
        coinOrderRepository.save(order);

        // 6. Credit Coins to Wallet!
        walletService.credit(
                userId,
                (long) order.getTotalCoins(), // Base + Bonus
                TransactionReason.PURCHASE,
                ReferenceType.PAYMENT,
                payment.getId(),
                "Purchased " + order.getPlanName()
        );
        
        // 7. Mark Idempotency complete
        idempotencyService.findByPaymentId(payment.getId())
                .ifPresent(record -> idempotencyService.markCompleted(record.getIdempotencyKey()));
        
        logger.info("[PAYMENT] Payment Verified & Coins Credited successfully for User={}", userId);
    }

    /**
     * HANDLE WEBHOOK (Server-to-Server from Razorpay)
     */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        logger.info("[PAYMENT WEBHOOK] Received webhook payload");

        try {
            // 1. Verify Signature
            boolean isValid = Utils.verifyWebhookSignature(payload, signature, razorpayWebhookSecret);
            if (!isValid) {
                logger.error("[PAYMENT WEBHOOK] Invalid signature detected!");
                return;
            }

            // 2. Parse Payload
            JSONObject jsonPayload = new JSONObject(payload);
            String event = jsonPayload.getString("event");
            String webhookEventId = jsonPayload.has("account_id") ? jsonPayload.optString("account_id") + "_" + event : "evt_" + UUID.randomUUID().toString();
            
            // Razorpay often sends webhook events with x-razorpay-event-id header, but here we can just ensure idempotency using the payload's event and entity IDs.
            // Let's extract the order ID and payment ID from the payload
            JSONObject payloadObj = jsonPayload.getJSONObject("payload");
            
            if (!event.equals("payment.captured")) {
                logger.info("[PAYMENT WEBHOOK] Ignoring event type: {}", event);
                return;
            }

            JSONObject paymentObj = payloadObj.getJSONObject("payment").getJSONObject("entity");
            String razorpayOrderId = paymentObj.getString("order_id");
            String razorpayPaymentId = paymentObj.getString("id");

            logger.info("[PAYMENT WEBHOOK] Processing payment.captured for Order={}", razorpayOrderId);

            // 3. Find Internal Payment
            Optional<Payment> paymentOpt = paymentRepository.findByGatewayOrderId(razorpayOrderId);
            if (paymentOpt.isEmpty()) {
                logger.warn("[PAYMENT WEBHOOK] Payment not found for Gateway Order ID: {}", razorpayOrderId);
                return;
            }
            
            Payment payment = paymentOpt.get();

            // 4. Double-Spend Protection (Webhook vs Frontend Race Condition)
            if (payment.isWalletCredited()) {
                logger.info("[PAYMENT WEBHOOK] Wallet already credited for Order={}. Skipping webhook.", razorpayOrderId);
                return;
            }

            // 5. Log Webhook (Idempotency check via DB unique constraint)
            // If the same event arrives twice, trying to save this will throw a DataIntegrityViolationException.
            PaymentWebhookLog webhookLog = PaymentWebhookLog.builder()
                    .payment(payment)
                    .provider(PaymentProvider.RAZORPAY)
                    .gatewayEventId(razorpayPaymentId + "_captured") // Unique per payment capture
                    .eventType(WebhookEventType.PAYMENT_CAPTURED)
                    .gatewaySignature(signature)
                    .payload(payload)
                    .status(WebhookStatus.PROCESSED)
                    .processedAt(java.time.LocalDateTime.now())
                    .build();
            
            try {
                webhookLogRepository.save(webhookLog);
            } catch (Exception e) {
                logger.info("[PAYMENT WEBHOOK] Webhook already processed (duplicate detected) for Event={}", webhookLog.getGatewayEventId());
                return;
            }

            // 6. Extract method and set details
            String methodStr = paymentObj.optString("method", "UNKNOWN");
            PaymentMethod methodEnum = PaymentMethod.UNKNOWN;
            try {
                methodEnum = PaymentMethod.valueOf(methodStr.toUpperCase());
            } catch (Exception e) {
                logger.warn("[PAYMENT WEBHOOK] Unknown payment method: {}", methodStr);
            }
            
            payment.setPaymentMethod(methodEnum);
            payment.setGatewayResponse(paymentObj.toString());
            payment.setGatewayPaymentId(razorpayPaymentId);
            payment.setGatewaySignature(signature); // Signature of webhook here, or original popup signature? Webhook signature is fine.
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setVerificationStatus(PaymentVerificationStatus.VERIFIED);
            payment.setWalletCredited(true);
            payment.setCompletedAt(java.time.LocalDateTime.now());

            paymentRepository.save(payment);

            // 7. Update CoinOrder Record
            CoinOrder order = payment.getOrder();
            order.setStatus(CoinOrderStatus.PAYMENT_SUCCESS);
            coinOrderRepository.save(order);

            // 8. Credit Coins to Wallet!
            walletService.credit(
                    order.getUser().getId(),
                    (long) order.getTotalCoins(), // Base + Bonus
                    TransactionReason.PURCHASE,
                    ReferenceType.PAYMENT,
                    payment.getId(),
                    "Purchased " + order.getPlanName() + " (Via Webhook)"
            );

            // 9. Mark Idempotency complete
            idempotencyService.findByPaymentId(payment.getId())
                    .ifPresent(record -> idempotencyService.markCompleted(record.getIdempotencyKey()));

            logger.info("[PAYMENT WEBHOOK] Successfully processed payment and credited coins for Order={}", razorpayOrderId);

        } catch (RazorpayException e) {
            logger.error("[PAYMENT WEBHOOK] Signature verification failed", e);
        } catch (Exception e) {
            logger.error("[PAYMENT WEBHOOK] Error processing webhook", e);
        }
    }
}
