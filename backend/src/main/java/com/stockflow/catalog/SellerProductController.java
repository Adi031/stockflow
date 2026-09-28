package com.stockflow.catalog;

import com.stockflow.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.stockflow.catalog.ProductDtos.*;

@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;
    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;

    @PostMapping
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        return productService.create(CurrentUser.id(), req);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @RequestBody UpdateProductRequest req) {
        return productService.update(CurrentUser.id(), id, req);
    }

    @PatchMapping("/{id}/restock")
    public ProductResponse restock(@PathVariable Long id, @Valid @RequestBody RestockRequest req) {
        return productService.restock(CurrentUser.id(), id, req.totalStock());
    }

    @GetMapping
    public Iterable<Product> myProducts() {
        Seller seller = sellerRepository.findByUserId(CurrentUser.id())
                .orElseThrow(() -> new com.stockflow.common.exception.ApiExceptions.NotFoundException("Seller profile not found"));
        return productRepository.findBySellerId(seller.getId());
    }
}
