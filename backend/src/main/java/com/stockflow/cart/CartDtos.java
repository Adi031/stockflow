package com.stockflow.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CartDtos {
    public record AddItemRequest(@NotNull Long productId, @Min(1) int quantity) {}
    public record CartItemView(Long productId, int quantity) {}
    public record CartView(Long cartId, List<CartItemView> items) {}
}
