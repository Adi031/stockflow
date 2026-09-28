package com.stockflow.catalog;

import com.stockflow.common.exception.ApiExceptions.NotFoundException;
import com.stockflow.common.exception.ApiExceptions.UnauthorizedException;
import com.stockflow.inventory.Inventory;
import com.stockflow.inventory.InventoryRepository;
import com.stockflow.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.stockflow.catalog.ProductDtos.*;

/**
 * Read-heavy catalog operations are cached in Redis (product detail, listing pages) because
 * product metadata changes rarely relative to how often it's read, and brief staleness (a
 * few seconds/minutes) on name/description/price is harmless. Live stock counts are
 * deliberately NOT cached here - InventoryService always reads/writes them straight from
 * Postgres under a row lock, since showing "in stock" after it's actually sold out is the one
 * kind of staleness this system cannot tolerate.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;

    @Cacheable(value = "product", key = "#id")
    public ProductResponse getById(Long id) {
        Product p = productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        return toResponse(p);
    }

    // Listing pages are cached per (query, category, page, size) key for a short TTL configured on the CacheManager.
    @Cacheable(value = "productSearch", key = "#query + ':' + #category + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PagedProducts search(String query, String category, Pageable pageable) {
        Page<ProductResponse> page = productRepository.search(query, category, pageable).map(this::toResponse);
        return new PagedProducts(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional
    @CacheEvict(value = {"product", "productSearch"}, allEntries = true)
    public ProductResponse create(Long sellerUserId, CreateProductRequest req) {
        Seller seller = sellerRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new NotFoundException("Seller profile not found"));
        Product p = Product.builder()
                .sellerId(seller.getId())
                .name(req.name())
                .description(req.description())
                .price(req.price())
                .category(req.category())
                .status("ACTIVE")
                .build();
        p = productRepository.save(p);
        inventoryService.createOrUpdateStock(p.getId(), req.initialStock());
        return toResponse(p);
    }

    @Transactional
    @CacheEvict(value = {"product", "productSearch"}, allEntries = true)
    public ProductResponse update(Long sellerUserId, Long productId, UpdateProductRequest req) {
        Product p = ownedProduct(sellerUserId, productId);
        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.price() != null) p.setPrice(req.price());
        if (req.category() != null) p.setCategory(req.category());
        return toResponse(productRepository.save(p));
    }

    @Transactional
    @CacheEvict(value = {"product", "productSearch"}, allEntries = true)
    public ProductResponse restock(Long sellerUserId, Long productId, int totalStock) {
        Product p = ownedProduct(sellerUserId, productId);
        inventoryService.createOrUpdateStock(p.getId(), totalStock);
        return toResponse(p);
    }

    private Product ownedProduct(Long sellerUserId, Long productId) {
        Seller seller = sellerRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new NotFoundException("Seller profile not found"));
        Product p = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("Product not found"));
        if (!p.getSellerId().equals(seller.getId())) {
            throw new UnauthorizedException("You do not own this product");
        }
        return p;
    }

    private ProductResponse toResponse(Product p) {
        Integer available = inventoryRepository.findByProductId(p.getId())
                .map(Inventory::getAvailableStock).orElse(0);
        return new ProductResponse(p.getId(), p.getSellerId(), p.getName(), p.getDescription(),
                p.getPrice(), p.getCategory(), p.getStatus(), available);
    }
}
