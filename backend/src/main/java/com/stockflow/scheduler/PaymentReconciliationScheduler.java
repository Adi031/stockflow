package com.stockflow.scheduler;

import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.Reservation;
import com.stockflow.order.Order;
import com.stockflow.order.OrderItemRepository;
import com.stockflow.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Resolves the "timeout" ambiguity: a payment that timed out might still complete on the
 * provider's side. In a real system this would poll the payment provider's status API; here we
 * simulate that by giving it a grace period and then treating any order still stuck in
 * PAYMENT_PROCESSING past that window as failed, releasing its reserved stock. This is the
 * concrete answer to "what happens when payment fails/hangs" for the timeout branch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;

    private static final int GRACE_PERIOD_MINUTES = 2;

    @Scheduled(fixedRateString = "${stockflow.scheduler.payment-reconciliation-fixed-rate-ms}")
    @Transactional
    public void reconcileStuckPayments() {
        var stuck = orderRepository.findByStatus(Order.Status.PAYMENT_PROCESSING);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(GRACE_PERIOD_MINUTES);
        for (Order order : stuck) {
            if (order.getUpdatedAt().isBefore(cutoff)) {
                orderItemRepository.findByOrderId(order.getId()).forEach(item -> {
                    if (item.getReservationId() != null) {
                        inventoryService.release(item.getReservationId(), Reservation.Status.CANCELLED);
                    }
                });
                order.setStatus(Order.Status.CANCELLED);
                orderRepository.save(order);
                log.info("Reconciled stuck payment-processing order {} -> CANCELLED, stock released", order.getId());
            }
        }
    }
}
