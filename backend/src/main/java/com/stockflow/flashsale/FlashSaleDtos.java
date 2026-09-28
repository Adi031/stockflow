package com.stockflow.flashsale;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class FlashSaleDtos {

    public record CreateFlashSaleRequest(
            @NotNull Long productId,
            @Min(1) int stockLimit,
            @Min(1) int perUserLimit,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt) {}

    public record PurchaseRequest(@Min(1) int quantity) {}

    public enum PurchaseResult { SUCCESS, SOLD_OUT, LIMIT_REACHED, NOT_ACTIVE }

    public record PurchaseResponse(PurchaseResult result, Long orderId) {}

    public record FlashSaleView(Long id, Long productId, int stockLimit, int perUserLimit,
                                 LocalDateTime startAt, LocalDateTime endAt, String status, Long remainingStock) {}
}
