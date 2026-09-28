package com.stockflow.order;

import com.stockflow.common.exception.ApiExceptions.NotFoundException;
import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.Reservation;
import com.stockflow.payment.Payment;
import com.stockflow.payment.PaymentSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.stockflow.order.OrderDtos.*;

/**
 * Orchestrates the full checkout flow: reserve stock -> create a PENDING order (idempotent on
 * the client-supplied key) -> attempt payment -> confirm or release based on outcome.
 *
 * Deliberately NOT a single @Transactional method: reserving stock, creating the order row, and
 * charging the (simulated, latency-including) payment each get their own short transaction so we
 * never hold a Postgres row lock across the payment call. Each step is individually consistent;
 * compensating actions (release reservations) handle the failure paths.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final PaymentSimulatorService paymentSimulatorService;
    private final OrderTransactionalOps txOps;

    public OrderResponse checkout(Long userId, String idempotencyKey, CheckoutRequest req) {
        // 1) Idempotency short-circuit: if this key already produced an order, return it unchanged.
        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        // 2) Reserve stock for every line item. Each reservation call takes its own row lock;
        //    if any line fails, we release everything already reserved in this attempt (compensation).
        List<Reservation> reservations = new ArrayList<>();
        try {
            for (CheckoutLine line : req.items()) {
                reservations.add(inventoryService.reserve(line.productId(), userId, line.quantity()));
            }
        } catch (RuntimeException ex) {
            for (Reservation r : reservations) {
                inventoryService.release(r.getId(), Reservation.Status.CANCELLED);
            }
            throw ex;
        }

        // 3) Create the PENDING order + items, keyed by the idempotency key.
        //    The idempotency_key column has a DB-level UNIQUE constraint, so even if two requests
        //    with the same key race past the check in step 1 concurrently, only one INSERT wins;
        //    the loser catches the constraint violation, discards its own (wasted) reservations,
        //    and returns the winner's order instead of creating a duplicate.
        Order order;
        try {
            order = txOps.createPendingOrder(userId, idempotencyKey, reservations);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            for (Reservation r : reservations) {
                inventoryService.release(r.getId(), Reservation.Status.CANCELLED);
            }
            return toResponse(orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(), true);
        }

        // 4) Attempt payment (outside any DB lock).
        txOps.transitionTo(order, Order.Status.PAYMENT_PROCESSING);
        Payment.Status forced = req.forcedOutcome() == null ? null : Payment.Status.valueOf(req.forcedOutcome().name());
        Payment payment = paymentSimulatorService.charge(order.getId(), forced);

        // 5) Resolve based on outcome.
        switch (payment.getStatus()) {
            case SUCCESS -> {
                reservations.forEach(r -> inventoryService.confirm(r.getId()));
                txOps.transitionTo(order, Order.Status.CONFIRMED);
            }
            case FAILED -> {
                reservations.forEach(r -> inventoryService.release(r.getId(), Reservation.Status.CANCELLED));
                txOps.transitionTo(order, Order.Status.CANCELLED);
            }
            case TIMEOUT -> log.warn("Payment timeout for order {} - left in PAYMENT_PROCESSING for reconciliation", order.getId());
            default -> { /* PENDING shouldn't be returned by the simulator */ }
        }

        return toResponse(orderRepository.findById(order.getId()).orElseThrow(), false);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        return toResponse(orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found")), false);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(o -> toResponse(o, false)).toList();
    }

    /** Seller/admin driven lifecycle progression (CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED, or cancel). */
    @Transactional
    public OrderResponse updateStatus(Long orderId, Order.Status newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (newStatus == Order.Status.CANCELLED) {
            orderItemRepository.findByOrderId(orderId).stream()
                    .map(OrderItem::getReservationId).filter(java.util.Objects::nonNull)
                    .forEach(rid -> inventoryService.release(rid, Reservation.Status.CANCELLED));
        }
        txOps.transitionTo(order, newStatus);
        return toResponse(order, false);
    }

    private OrderResponse toResponse(Order order, boolean deduplicated) {
        List<OrderItemView> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(i -> new OrderItemView(i.getProductId(), i.getQuantity(), i.getUnitPrice())).toList();
        return new OrderResponse(order.getId(), order.getStatus().name(), order.getTotalAmount(),
                items, order.getCreatedAt(), deduplicated);
    }
}
