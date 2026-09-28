package com.stockflow.order;

import com.stockflow.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.stockflow.order.OrderDtos.*;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final OrderService orderService;

    /**
     * Idempotency-Key header is required: the client should generate one UUID per checkout
     * attempt and resend the SAME key on retry (network timeout, double-click, etc). Repeating
     * the call with the same key always returns the original order rather than creating a new one.
     */
    @PostMapping
    public OrderResponse checkout(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @Valid @RequestBody CheckoutRequest req) {
        return orderService.checkout(CurrentUser.id(), idempotencyKey, req);
    }
}
