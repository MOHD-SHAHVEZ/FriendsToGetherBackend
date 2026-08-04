package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Long> {

    Optional<PaymentWebhookLog> findByGatewayEventId(String gatewayEventId);
}
