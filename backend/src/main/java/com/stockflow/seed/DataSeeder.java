package com.stockflow.seed;

import com.stockflow.auth.User;
import com.stockflow.auth.UserRepository;
import com.stockflow.catalog.Product;
import com.stockflow.catalog.ProductRepository;
import com.stockflow.catalog.Seller;
import com.stockflow.catalog.SellerRepository;
import com.stockflow.flashsale.FlashSaleDtos.CreateFlashSaleRequest;
import com.stockflow.flashsale.FlashSaleService;
import com.stockflow.inventory.InventoryService;
import com.stockflow.order.Order;
import com.stockflow.order.OrderDtos.CheckoutLine;
import com.stockflow.order.OrderDtos.CheckoutRequest;
import com.stockflow.order.OrderDtos.OrderResponse;
import com.stockflow.order.OrderDtos.PaymentOutcome;
import com.stockflow.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads demo data on startup when stockflow.seed.enabled=true (env: SEED_ENABLED=true).
 * Safe to leave enabled: it does nothing if any user already exists.
 * Every account uses the password below - demo use only.
 */
@Component
@ConditionalOnProperty(name = "stockflow.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    static final String PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final FlashSaleService flashSaleService;
    private final PasswordEncoder passwordEncoder;

    private record Item(String name, String description, String price, String category, int stock) {}

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Seed skipped: database already contains users");
            return;
        }
        log.info("Seeding demo data...");
        String hash = passwordEncoder.encode(PASSWORD);

        createUser("admin@stockflow.dev", "Admin User", User.Role.ADMIN, hash);

        String[] stores = {"TechNest", "UrbanThreads", "HomeHaven"};
        List<Seller> sellers = new ArrayList<>();
        for (int i = 0; i < stores.length; i++) {
            User u = createUser("seller" + (i + 1) + "@stockflow.dev", "Seller " + (i + 1), User.Role.SELLER, hash);
            sellers.add(sellerRepository.save(Seller.builder()
                    .userId(u.getId()).storeName(stores[i]).status("ACTIVE").build()));
        }

        List<User> customers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            customers.add(createUser("customer" + i + "@stockflow.dev", "Customer " + i, User.Role.CUSTOMER, hash));
        }

        List<List<Item>> catalog = List.of(
                List.of(
                        new Item("Wireless Noise-Cancelling Headphones", "40h battery, ANC", "129.99", "electronics", 50),
                        new Item("Mechanical Keyboard 75%", "Hot-swappable switches", "89.00", "electronics", 25),
                        new Item("USB-C Docking Station", "9-in-1 hub, 100W passthrough", "64.50", "electronics", 100),
                        new Item("27\" 4K Monitor", "IPS, 60Hz", "329.00", "electronics", 3),
                        new Item("Portable SSD 1TB", "1050MB/s read", "94.99", "electronics", 40),
                        new Item("Smart Watch S2", "GPS, heart-rate", "179.00", "electronics", 0),
                        new Item("Webcam 1080p", "Autofocus, dual mic", "49.99", "electronics", 60),
                        new Item("Bluetooth Speaker", "Waterproof, 12h", "39.99", "electronics", 80)),
                List.of(
                        new Item("Classic Denim Jacket", "Unisex, mid-wash", "74.00", "apparel", 35),
                        new Item("Running Shoes Pro", "Lightweight mesh", "110.00", "shoes", 45),
                        new Item("Organic Cotton Tee", "Pack of 2", "24.99", "apparel", 200),
                        new Item("Leather Sneakers", "Handmade, white", "138.00", "shoes", 4),
                        new Item("Wool Beanie", "One size", "18.50", "apparel", 90),
                        new Item("Canvas Backpack", "20L, water-resistant", "56.00", "accessories", 30),
                        new Item("Aviator Sunglasses", "Polarized", "62.00", "accessories", 55),
                        new Item("Trail Hiking Boots", "Gore-Tex", "149.00", "shoes", 15)),
                List.of(
                        new Item("Ceramic Pour-Over Set", "Dripper + carafe", "42.00", "home", 40),
                        new Item("Linen Duvet Cover", "Queen, stone grey", "98.00", "home", 20),
                        new Item("Cast Iron Skillet 12\"", "Pre-seasoned", "36.00", "kitchen", 70),
                        new Item("LED Desk Lamp", "Dimmable, USB charging", "29.99", "home", 120),
                        new Item("Scented Soy Candle", "Cedar & amber", "16.00", "home", 150),
                        new Item("Bamboo Cutting Board", "Large, juice groove", "27.50", "kitchen", 65),
                        new Item("Robot Vacuum X1", "Mapping, auto-empty", "299.00", "home", 2),
                        new Item("Ceramic Planter Set", "Set of 3", "34.00", "home", 45)));

        List<Product> products = new ArrayList<>();
        for (int s = 0; s < sellers.size(); s++) {
            for (Item it : catalog.get(s)) {
                products.add(saveProduct(sellers.get(s), it));
            }
        }
        Product flashProduct = saveProduct(sellers.get(0),
                new Item("Limited Edition Mechanical Keyboard", "Numbered run - flash sale item", "249.00", "electronics", 50));

        seedOrders(customers, products);
        seedFlashSale(flashProduct);

        log.info("Seed complete. Log in as admin@stockflow.dev / seller1@stockflow.dev / customer1@stockflow.dev with password '{}'", PASSWORD);
    }

    private User createUser(String email, String name, User.Role role, String hash) {
        return userRepository.save(User.builder().email(email).fullName(name).role(role).passwordHash(hash).build());
    }

    private Product saveProduct(Seller seller, Item it) {
        Product p = productRepository.save(Product.builder()
                .sellerId(seller.getId()).name(it.name()).description(it.description())
                .price(new BigDecimal(it.price())).category(it.category()).status("ACTIVE").build());
        inventoryService.createOrUpdateStock(p.getId(), it.stock());
        return p;
    }

    /** Runs real checkouts (forced payment outcomes) so orders, payments and reservations are consistent. */
    private void seedOrders(List<User> customers, List<Product> products) {
        placeOrder(1, customers.get(0), PaymentOutcome.SUCCESS, List.of(line(products, 0, 1), line(products, 1, 2)),
                Order.Status.PROCESSING, Order.Status.SHIPPED, Order.Status.DELIVERED);
        placeOrder(2, customers.get(1), PaymentOutcome.SUCCESS, List.of(line(products, 8, 1)),
                Order.Status.PROCESSING, Order.Status.SHIPPED);
        placeOrder(3, customers.get(2), PaymentOutcome.SUCCESS, List.of(line(products, 10, 3)),
                Order.Status.PROCESSING);
        placeOrder(4, customers.get(3), PaymentOutcome.SUCCESS, List.of(line(products, 17, 1)));
        placeOrder(5, customers.get(4), PaymentOutcome.FAILED, List.of(line(products, 2, 1)));
        placeOrder(6, customers.get(0), PaymentOutcome.SUCCESS, List.of(line(products, 18, 2)));
    }

    private CheckoutLine line(List<Product> products, int index, int qty) {
        return new CheckoutLine(products.get(index).getId(), qty);
    }

    private void placeOrder(int n, User customer, PaymentOutcome outcome, List<CheckoutLine> lines, Order.Status... advanceTo) {
        try {
            OrderResponse res = orderService.checkout(customer.getId(), "seed-order-" + n, new CheckoutRequest(lines, outcome));
            for (Order.Status next : advanceTo) {
                orderService.updateStatus(res.id(), next);
            }
        } catch (Exception e) {
            log.warn("Seed order {} skipped: {}", n, e.getMessage());
        }
    }

    private void seedFlashSale(Product product) {
        try {
            var sale = flashSaleService.create(new CreateFlashSaleRequest(
                    product.getId(), 5, 1, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1)));
            flashSaleService.activate(sale.id());
            log.info("Flash sale #{} is live: 5 units of '{}'", sale.id(), product.getName());
        } catch (Exception e) {
            log.warn("Flash sale seed skipped (is Redis running?): {}", e.getMessage());
        }
    }
}