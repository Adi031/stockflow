package com.stockflow.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a real payment gateway. Deterministic outcomes can be forced (used heavily in
 * tests); otherwise outcome is randomized by the configured rates to exercise all three branches
 * that OrderService's state machine must handle: SUCCESS, FAILED, TIMEOUT.
 */
@Service
@RequiredArgsConstructor
public class PaymentSimulatorService {

    private final PaymentRepository paymentRepository;

    @Value("${stockflow.payment.simulated-latency-ms}")
    private long latencyMs;

    @Value("${stockflow.payment.failure-rate}")
    private double failureRate;

    @Value("${stockflow.payment.timeout-rate}")
    private double timeoutRate;

    /** Charge synchronously with a randomized (or forced) outcome. */
    @Transactional
    public Payment charge(Long orderId, Payment.Status forcedOutcome) {
        simulateLatency();
        Payment.Status outcome = forcedOutcome != null ? forcedOutcome : rollOutcome();
        Payment payment = Payment.builder()
                .orderId(orderId)
                .status(outcome)
                .providerRef(UUID.randomUUID().toString())
                .build();
        return paymentRepository.save(payment);
    }

    private Payment.Status rollOutcome() {
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < timeoutRate) return Payment.Status.TIMEOUT;
        if (roll < timeoutRate + failureRate) return Payment.Status.FAILED;
        return Payment.Status.SUCCESS;
    }

    private void simulateLatency() {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
