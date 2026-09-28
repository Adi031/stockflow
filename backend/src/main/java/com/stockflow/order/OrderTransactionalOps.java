package com.stockflow.order;

import com.stockflow.catalog.Product;
import com.stockflow.catalog.ProductRepository;
import com.stockflow.common.exception.ApiExceptions.NotFoundException;
import com.stockflow.inventory.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Extracted out of OrderService so Spring's @Transactional proxy actually applies.
 * (Calling an @Transactional method via `this.method()` from within the same bean bypasses
 * the proxy entirely - a classic Spring pitfall. Injecting this as a separate bean sidesteps it.)
 */
@Component
@RequiredArgsConstructor
class OrderTransactionalOps {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final OrderStateMachine stateMachine;

    @Transactional
    Order createPendingOrder(Long userId, String idempotencyKey, List<Reservation> reservations) {
        BigDecimal total = BigDecimal.ZERO;
        Order order = orderRepository.save(Order.builder()
                .userId(userId).idempotencyKey(idempotencyKey).status(Order.Status.PENDING).totalAmount(total).build());

        for (Reservation r : reservations) {
            Product product = productRepository.findById(r.getProductId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + r.getProductId()));
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(r.getQuantity()));
            total = total.add(lineTotal);
            orderItemRepository.save(OrderItem.builder()
                    .orderId(order.getId()).productId(r.getProductId()).reservationId(r.getId())
                    .quantity(r.getQuantity()).unitPrice(product.getPrice()).build());
        }
        order.setTotalAmount(total);
        return orderRepository.save(order);
    }

    @Transactional
    void transitionTo(Order order, Order.Status newStatus) {
        stateMachine.assertLegal(order.getStatus(), newStatus);
        order.setStatus(newStatus);
        orderRepository.save(order);
    }
}
