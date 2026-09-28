package com.stockflow.flashsale;

import com.stockflow.common.exception.ApiExceptions.NotFoundException;
import com.stockflow.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.stockflow.flashsale.FlashSaleDtos.*;

/**
 * Flash sale purchase path is intentionally different from normal checkout: instead of taking a
 * Postgres row lock per request (InventoryService.reserve), every purchase attempt is gated by a
 * single atomic Redis Lua script (flash_sale_purchase.lua). Redis processes commands/scripts
 * single-threaded, so the check-stock + check-user-limit + decrement sequence is race-free without
 * any database contention - this is what lets the endpoint survive thousands of concurrent
 * requests against a handful of units. Postgres is only written to once per WINNING request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleOrderRepository flashSaleOrderRepository;
    private final InventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;
    private final FlashSaleTransactionalOps txOps;

    private final DefaultRedisScript<Long> purchaseScript = loadScript();

    private DefaultRedisScript<Long> loadScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/flash_sale_purchase.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Transactional
    public FlashSaleView create(CreateFlashSaleRequest req) {
        FlashSale fs = flashSaleRepository.save(FlashSale.builder()
                .productId(req.productId()).stockLimit(req.stockLimit()).perUserLimit(req.perUserLimit())
                .startAt(req.startAt()).endAt(req.endAt()).status("SCHEDULED").build());
        return toView(fs);
    }

    /** Carves stock out of the normal pool and seeds the Redis counter. Call at (or just before) start_at. */
    @Transactional
    public FlashSaleView activate(Long flashSaleId) {
        FlashSale fs = flashSaleRepository.findById(flashSaleId)
                .orElseThrow(() -> new NotFoundException("Flash sale not found"));
        if (!"SCHEDULED".equals(fs.getStatus())) return toView(fs);

        inventoryService.carveOutForFlashSale(fs.getProductId(), fs.getStockLimit());
        redisTemplate.opsForValue().set(stockKey(fs.getId()), String.valueOf(fs.getStockLimit()));
        fs.setStatus("ACTIVE");
        flashSaleRepository.save(fs);
        log.info("Flash sale {} activated: {} units gated via Redis", fs.getId(), fs.getStockLimit());
        return toView(fs);
    }

    public PurchaseResponse purchase(Long flashSaleId, Long userId, int quantity) {
        FlashSale fs = flashSaleRepository.findById(flashSaleId)
                .orElseThrow(() -> new NotFoundException("Flash sale not found"));

        if (!"ACTIVE".equals(fs.getStatus()) || LocalDateTime.now().isAfter(fs.getEndAt())) {
            return new PurchaseResponse(PurchaseResult.NOT_ACTIVE, null);
        }

        Long result = redisTemplate.execute(purchaseScript,
                List.of(stockKey(fs.getId()), userKey(fs.getId(), userId)),
                String.valueOf(quantity), String.valueOf(fs.getPerUserLimit()));

        if (result == null || result == 0L) {
            return new PurchaseResponse(PurchaseResult.SOLD_OUT, null);
        }
        if (result == -1L) {
            return new PurchaseResponse(PurchaseResult.LIMIT_REACHED, null);
        }

        // result == 1: Redis already committed to this sale; persist the outcome to Postgres.
        Long orderId = txOps.persistWin(fs, userId, quantity);
        return new PurchaseResponse(PurchaseResult.SUCCESS, orderId);
    }

    public FlashSaleView getStats(Long flashSaleId) {
        FlashSale fs = flashSaleRepository.findById(flashSaleId)
                .orElseThrow(() -> new NotFoundException("Flash sale not found"));
        return toView(fs);
    }

    private FlashSaleView toView(FlashSale fs) {
        String remaining = redisTemplate.opsForValue().get(stockKey(fs.getId()));
        return new FlashSaleView(fs.getId(), fs.getProductId(), fs.getStockLimit(), fs.getPerUserLimit(),
                fs.getStartAt(), fs.getEndAt(), fs.getStatus(), remaining == null ? null : Long.valueOf(remaining));
    }

    private String stockKey(Long flashSaleId) { return "flashsale:" + flashSaleId + ":stock"; }
    private String userKey(Long flashSaleId, Long userId) { return "flashsale:" + flashSaleId + ":user:" + userId; }
}
