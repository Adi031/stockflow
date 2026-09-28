package com.stockflow.flashsale;

import com.stockflow.catalog.ProductRepository;
import com.stockflow.inventory.InventoryService;
import com.stockflow.order.Order;
import com.stockflow.order.OrderItem;
import com.stockflow.order.OrderItemRepository;
import com.stockflow.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Extracted to its own bean so @Transactional applies (see note in OrderTransactionalOps). */
@Component
@RequiredArgsConstructor
class FlashSaleTransactionalOps {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final FlashSaleOrderRepository flashSaleOrderRepository;

    @Transactional
    Long persistWin(FlashSale fs, Long userId, int quantity) {
        inventoryService.confirmFlashSalePurchase(fs.getProductId(), quantity);

        var product = productRepository.findById(fs.getProductId()).orElseThrow();
        Order order = orderRepository.save(Order.builder()
                .userId(userId)
                .idempotencyKey("flashsale-" + fs.getId() + "-" + userId + "-" + UUID.randomUUID())
                .status(Order.Status.CONFIRMED)
                .totalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .build());
        orderItemRepository.save(OrderItem.builder()
                .orderId(order.getId()).productId(fs.getProductId())
                .quantity(quantity).unitPrice(product.getPrice()).build());

        flashSaleOrderRepository.save(FlashSaleOrderRecord.builder()
                .flashSaleId(fs.getId()).userId(userId).orderId(order.getId()).quantity(quantity).build());

        return order.getId();
    }
}
