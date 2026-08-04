package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
    
    Optional<Payment> findByPaymentReference(String paymentReference);
    
    List<Payment> findByOrder_Id(Long orderId);
}
