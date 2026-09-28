package com.stockflow.cart;

import com.stockflow.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.stockflow.cart.CartDtos.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartView get() {
        return cartService.getCart(CurrentUser.id());
    }

    @PostMapping("/items")
    public CartView addItem(@Valid @RequestBody AddItemRequest req) {
        return cartService.addItem(CurrentUser.id(), req);
    }

    @DeleteMapping("/items/{id}")
    public void removeItem(@PathVariable Long id) {
        cartService.removeItem(CurrentUser.id(), id);
    }
}
