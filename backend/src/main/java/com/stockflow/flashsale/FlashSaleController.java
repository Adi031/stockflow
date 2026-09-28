package com.stockflow.flashsale;

import com.stockflow.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.stockflow.flashsale.FlashSaleDtos.*;

@RestController
@RequestMapping("/api/flash-sales")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public FlashSaleView create(@Valid @RequestBody CreateFlashSaleRequest req) {
        return flashSaleService.create(req);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public FlashSaleView activate(@PathVariable Long id) {
        return flashSaleService.activate(id);
    }

    /** Open to any authenticated customer - this is the hot endpoint under flash-sale load. */
    @PostMapping("/{id}/purchase")
    public PurchaseResponse purchase(@PathVariable Long id, @Valid @RequestBody PurchaseRequest req) {
        return flashSaleService.purchase(id, CurrentUser.id(), req.quantity());
    }

    @GetMapping("/{id}/stats")
    public FlashSaleView stats(@PathVariable Long id) {
        return flashSaleService.getStats(id);
    }
}
