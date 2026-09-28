package com.stockflow.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public class ProductDtos {

    public record CreateProductRequest(
            @NotBlank String name,
            String description,
            @DecimalMin("0.0") BigDecimal price,
            String category,
            @Min(0) int initialStock) {}

    public record UpdateProductRequest(String name, String description, BigDecimal price, String category) {}

    public record ProductResponse(
            Long id, Long sellerId, String name, String description, BigDecimal price,
            String category, String status, Integer availableStock) {}

    public record PagedProducts(java.util.List<ProductResponse> content, int page, int size, long totalElements) {}

    public record RestockRequest(@Min(0) int totalStock) {}
}
