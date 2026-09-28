package com.stockflow.flashsale;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashSaleOrderRepository extends JpaRepository<FlashSaleOrderRecord, Long> {
}
