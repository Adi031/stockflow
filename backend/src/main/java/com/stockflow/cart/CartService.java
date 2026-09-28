package com.stockflow.cart;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.stockflow.cart.CartDtos.*;

/**
 * The cart itself does NOT touch inventory - adding to cart is a cheap, no-lock operation.
 * Stock is only checked/reserved at checkout time (see InventoryService.reserve), which keeps
 * browsing/cart-building fast and avoids reserving stock for carts that are never checked out.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public CartView addItem(Long userId, AddItemRequest req) {
        Cart cart = activeCart(userId);
        var existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), req.productId());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + req.quantity());
            cartItemRepository.save(item);
        } else {
            cartItemRepository.save(CartItem.builder()
                    .cartId(cart.getId()).productId(req.productId()).quantity(req.quantity()).build());
        }
        return view(cart);
    }

    @Transactional
    public void removeItem(Long userId, Long cartItemId) {
        Cart cart = activeCart(userId);
        cartItemRepository.findById(cartItemId)
                .filter(i -> i.getCartId().equals(cart.getId()))
                .ifPresent(cartItemRepository::delete);
    }

    public CartView getCart(Long userId) {
        return view(activeCart(userId));
    }

    @Transactional
    public Cart activeCart(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, "ACTIVE")
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).status("ACTIVE").build()));
    }

    private CartView view(Cart cart) {
        List<CartItemView> items = cartItemRepository.findByCartId(cart.getId()).stream()
                .map(i -> new CartItemView(i.getProductId(), i.getQuantity()))
                .toList();
        return new CartView(cart.getId(), items);
    }
}
