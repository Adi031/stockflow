package com.stockflow.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDtos {

    public record CheckoutLine(@NotNull Long productId, @Min(1) int quantity) {}

    public record CheckoutRequest(@NotEmpty List<CheckoutLine> items, PaymentOutcome forcedOutcome) {}

    public enum PaymentOutcome { SUCCESS, FAILED, TIMEOUT }

    public record OrderItemView(Long productId, int quantity, BigDecimal unitPrice) {}

    public record OrderResponse(
            Long id, String status, BigDecimal totalAmount,
            List<OrderItemView> items, LocalDateTime createdAt, boolean deduplicated) {}

    public record UpdateStatusRequest(Order.Status status) {}
}
