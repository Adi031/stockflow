package com.stockflow.inventory;

import com.stockflow.common.exception.ApiExceptions.InsufficientStockException;
import com.stockflow.common.exception.ApiExceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Owns every mutation of stock counts. Every method here runs inside a single DB transaction
 * that takes a PESSIMISTIC_WRITE lock on the product's inventory row first (SELECT ... FOR UPDATE),
 * so concurrent requests for the same product are serialized at the row level and can never both
 * observe/act on the same "available_stock" value. This is what prevents overselling on the
 * normal (non-flash-sale) checkout path.
 *
 * Invariant enforced on every write: available_stock + reserved_stock <= total_stock (also a DB CHECK constraint).
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;

    @Value("${stockflow.reservation.ttl-minutes}")
    private int reservationTtlMinutes;

    /**
     * Reserve `quantity` units of a product for a user. Fails fast with InsufficientStockException
     * if not enough is available - the row lock guarantees this check-then-act is atomic across
     * concurrent callers.
     */
    @Transactional
    public Reservation reserve(Long productId, Long userId, int quantity) {
        Inventory inv = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("No inventory record for product " + productId));

        if (inv.getAvailableStock() < quantity) {
            throw new InsufficientStockException(
                    "Only " + inv.getAvailableStock() + " units of product " + productId + " available");
        }

        inv.setAvailableStock(inv.getAvailableStock() - quantity);
        inv.setReservedStock(inv.getReservedStock() + quantity);
        inventoryRepository.save(inv);

        Reservation reservation = Reservation.builder()
                .productId(productId)
                .userId(userId)
                .quantity(quantity)
                .status(Reservation.Status.ACTIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(reservationTtlMinutes))
                .build();
        return reservationRepository.save(reservation);
    }

    /** Reservation converts into a real purchase: stock permanently leaves reserved_stock (not returned to available). */
    @Transactional
    public void confirm(Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        if (r.getStatus() != Reservation.Status.ACTIVE) return; // idempotent no-op if already confirmed/released

        Inventory inv = inventoryRepository.findByProductIdForUpdate(r.getProductId())
                .orElseThrow(() -> new NotFoundException("No inventory record for product " + r.getProductId()));

        inv.setReservedStock(inv.getReservedStock() - r.getQuantity());
        inv.setTotalStock(inv.getTotalStock() - r.getQuantity()); // stock has left the building
        inventoryRepository.save(inv);

        r.setStatus(Reservation.Status.CONFIRMED);
        reservationRepository.save(r);
    }

    /** Release: reservation failed to convert (payment failed/timeout/cart abandoned/expired). Stock returns to available. */
    @Transactional
    public void release(Long reservationId, Reservation.Status terminalStatus) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
        if (r.getStatus() != Reservation.Status.ACTIVE) return; // idempotent

        Inventory inv = inventoryRepository.findByProductIdForUpdate(r.getProductId())
                .orElseThrow(() -> new NotFoundException("No inventory record for product " + r.getProductId()));

        inv.setAvailableStock(inv.getAvailableStock() + r.getQuantity());
        inv.setReservedStock(inv.getReservedStock() - r.getQuantity());
        inventoryRepository.save(inv);

        r.setStatus(terminalStatus);
        reservationRepository.save(r);
    }

    /** Called by the scheduled sweep job. Releases every reservation whose TTL has passed. */
    @Transactional
    public int releaseExpired() {
        var expired = reservationRepository.findByStatusAndExpiresAtBefore(
                Reservation.Status.ACTIVE, LocalDateTime.now());
        for (Reservation r : expired) {
            release(r.getId(), Reservation.Status.EXPIRED);
        }
        return expired.size();
    }

    /**
     * Carves out `quantity` units from available_stock into a dedicated flash-sale pool at
     * sale-activation time. After this call, that stock is no longer visible/purchasable through
     * the normal checkout path - Redis becomes the sole gatekeeper for it (see FlashSaleService).
     */
    @Transactional
    public void carveOutForFlashSale(Long productId, int quantity) {
        Inventory inv = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("No inventory record for product " + productId));
        if (inv.getAvailableStock() < quantity) {
            throw new InsufficientStockException("Not enough available stock to fund flash sale for product " + productId);
        }
        inv.setAvailableStock(inv.getAvailableStock() - quantity);
        inv.setReservedStock(inv.getReservedStock() + quantity);
        inventoryRepository.save(inv);
    }

    /**
     * Called once per successful Redis-gated flash-sale purchase to persist the outcome to
     * Postgres. Redis already guaranteed at most `stockLimit` callers ever reach this method for
     * a given flash sale, so contention here is bounded and far lower than the raw request volume.
     */
    @Transactional
    public void confirmFlashSalePurchase(Long productId, int quantity) {
        Inventory inv = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("No inventory record for product " + productId));
        inv.setReservedStock(inv.getReservedStock() - quantity);
        inv.setTotalStock(inv.getTotalStock() - quantity);
        inventoryRepository.save(inv);
    }

    @Transactional
    public Inventory createOrUpdateStock(Long productId, int totalStock) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder().productId(productId).availableStock(0).reservedStock(0).totalStock(0).build());
        int delta = totalStock - inv.getTotalStock();
        inv.setTotalStock(totalStock);
        inv.setAvailableStock(inv.getAvailableStock() + delta);
        return inventoryRepository.save(inv);
    }
}
