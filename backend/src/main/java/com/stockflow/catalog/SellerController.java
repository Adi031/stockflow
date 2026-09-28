package com.stockflow.catalog;

import com.stockflow.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerRepository sellerRepository;

    public record CreateSellerProfileRequest(@NotBlank String storeName) {}

    /** A user with SELLER role calls this once to open their store. */
    @PostMapping("/profile")
    @PreAuthorize("hasRole('SELLER')")
    public Seller createProfile(@RequestBody CreateSellerProfileRequest req) {
        return sellerRepository.findByUserId(CurrentUser.id())
                .orElseGet(() -> sellerRepository.save(Seller.builder()
                        .userId(CurrentUser.id()).storeName(req.storeName()).status("ACTIVE").build()));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('SELLER')")
    public Seller myProfile() {
        return sellerRepository.findByUserId(CurrentUser.id())
                .orElseThrow(() -> new com.stockflow.common.exception.ApiExceptions.NotFoundException("Seller profile not found"));
    }
}
