package com.stockflow.order;

import com.stockflow.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.stockflow.order.OrderDtos.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<OrderResponse> myOrders() {
        return orderService.myOrders(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getById(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public OrderResponse updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest req) {
        return orderService.updateStatus(id, req.status());
    }
}
