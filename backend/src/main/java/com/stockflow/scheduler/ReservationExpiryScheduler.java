package com.stockflow.scheduler;

import com.stockflow.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically sweeps ACTIVE reservations past their expires_at and returns their stock to
 * available_stock. This is the "automatic expiry" half of the reservation system - without it,
 * abandoned checkouts would permanently lock stock away from other buyers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {

    private final InventoryService inventoryService;

    @Scheduled(fixedRateString = "${stockflow.scheduler.reservation-sweep-fixed-rate-ms}")
    public void sweepExpiredReservations() {
        int released = inventoryService.releaseExpired();
        if (released > 0) {
            log.info("Released {} expired reservation(s) back to available stock", released);
        }
    }
}
