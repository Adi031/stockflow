package com.stockflow.admin;

import com.stockflow.auth.User;
import com.stockflow.auth.UserRepository;
import com.stockflow.catalog.Product;
import com.stockflow.catalog.ProductRepository;
import com.stockflow.order.Order;
import com.stockflow.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @GetMapping("/users")
    public List<User> users() {
        return userRepository.findAll();
    }

    @GetMapping("/products")
    public List<Product> products() {
        return productRepository.findAll();
    }

    @GetMapping("/orders")
    public List<Order> orders() {
        return orderRepository.findAll();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Order> all = orderRepository.findAll();
        long confirmed = all.stream().filter(o -> o.getStatus() == Order.Status.CONFIRMED
                || o.getStatus() == Order.Status.PROCESSING || o.getStatus() == Order.Status.SHIPPED
                || o.getStatus() == Order.Status.DELIVERED).count();
        long cancelled = all.stream().filter(o -> o.getStatus() == Order.Status.CANCELLED).count();
        long pendingPayment = all.stream().filter(o -> o.getStatus() == Order.Status.PAYMENT_PROCESSING).count();
        return Map.of(
                "totalUsers", userRepository.count(),
                "totalProducts", productRepository.count(),
                "totalOrders", all.size(),
                "confirmedOrders", confirmed,
                "cancelledOrders", cancelled,
                "ordersAwaitingPayment", pendingPayment
        );
    }
}
