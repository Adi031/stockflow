package com.stockflow.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByOrderIdOrderByIdDesc(Long orderId);
    List<Payment> findByStatus(Payment.Status status);
}
