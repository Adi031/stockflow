package com.stockflow.concurrency;

import com.stockflow.catalog.Product;
import com.stockflow.catalog.ProductRepository;
import com.stockflow.catalog.Seller;
import com.stockflow.catalog.SellerRepository;
import com.stockflow.common.exception.ApiExceptions.InsufficientStockException;
import com.stockflow.inventory.Inventory;
import com.stockflow.inventory.InventoryRepository;
import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE test that justifies the whole "concurrency control" part of this project.
 *
 * Seeds a product with a small amount of stock (10 units), then fires 200 concurrent reservation
 * attempts of 1 unit each at it via a real thread pool hitting the real (Testcontainers) Postgres
 * instance through InventoryService.reserve(), which takes a SELECT ... FOR UPDATE row lock.
 *
 * Assertion: exactly 10 succeed, the rest fail with InsufficientStockException, and the final
 * database state satisfies available_stock + reserved_stock == total_stock. If the pessimistic
 * lock were removed (e.g. swapped for a plain SELECT then UPDATE), this test fails intermittently
 * with more than 10 successful reservations - i.e. overselling.
 */
@SpringBootTest
@Testcontainers
class ConcurrentCheckoutOversellTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("stockflow_test").withUsername("test").withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private com.stockflow.auth.UserRepository userRepository;

    private Long productId;
    private static final int STOCK = 10;
    private static final int CONCURRENT_BUYERS = 200;

    @BeforeEach
    void seed() {
        var user = userRepository.save(com.stockflow.auth.User.builder()
                .email("seller-" + System.nanoTime() + "@test.com").passwordHash("x")
                .role(com.stockflow.auth.User.Role.SELLER).fullName("Test Seller").build());
        Seller seller = sellerRepository.save(Seller.builder().userId(user.getId()).storeName("Test Store").status("ACTIVE").build());
        Product product = productRepository.save(Product.builder()
                .sellerId(seller.getId()).name("Limited Sneaker").price(BigDecimal.valueOf(199.99))
                .category("shoes").status("ACTIVE").build());
        productId = product.getId();
        inventoryRepository.save(Inventory.builder()
                .productId(productId).totalStock(STOCK).availableStock(STOCK).reservedStock(0).build());
    }

    @Test
    void concurrentReservations_neverExceedAvailableStock() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_BUYERS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_BUYERS; i++) {
            final long buyerId = 1000 + i;
            pool.submit(() -> {
                try {
                    startGate.await(); // all threads fire as close to simultaneously as possible
                    Reservation r = inventoryService.reserve(productId, buyerId, 1);
                    if (r != null) successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all 200 threads at once
        boolean finished = doneLatch.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finished, "All concurrent reservation attempts should complete within timeout");
        assertEquals(STOCK, successCount.get(), "Exactly STOCK reservations should succeed - no more, no less");
        assertEquals(CONCURRENT_BUYERS - STOCK, rejectedCount.get(), "The remaining attempts must be rejected, never silently oversold");

        Inventory finalState = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(0, finalState.getAvailableStock(), "All stock should now be reserved");
        assertEquals(STOCK, finalState.getReservedStock());
        assertEquals(finalState.getTotalStock(), finalState.getAvailableStock() + finalState.getReservedStock(),
                "Core invariant available+reserved==total must hold after concurrent contention");
    }
}
