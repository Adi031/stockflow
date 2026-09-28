CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER','SELLER','ADMIN')),
    full_name     VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE sellers (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users(id),
    store_name VARCHAR(255) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    seller_id   BIGINT NOT NULL REFERENCES sellers(id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    category    VARCHAR(100),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_name_trgm ON products USING gin (to_tsvector('english', name));

CREATE TABLE inventory (
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT NOT NULL UNIQUE REFERENCES products(id),
    total_stock      INTEGER NOT NULL CHECK (total_stock >= 0),
    available_stock  INTEGER NOT NULL CHECK (available_stock >= 0),
    reserved_stock   INTEGER NOT NULL CHECK (reserved_stock >= 0),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_stock_sum CHECK (available_stock + reserved_stock <= total_stock)
);

CREATE TABLE carts (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    UNIQUE(cart_id, product_id)
);

CREATE TABLE reservations (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    user_id    BIGINT NOT NULL REFERENCES users(id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CONFIRMED','EXPIRED','CANCELLED')),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_reservations_sweep ON reservations(status, expires_at);

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PAYMENT_PROCESSING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    reservation_id BIGINT REFERENCES reservations(id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL
);

CREATE TABLE payments (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED','TIMEOUT')),
    provider_ref  VARCHAR(64),
    attempted_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE flash_sales (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    stock_limit     INTEGER NOT NULL CHECK (stock_limit > 0),
    per_user_limit  INTEGER NOT NULL DEFAULT 1,
    start_at        TIMESTAMP NOT NULL,
    end_at          TIMESTAMP NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','ACTIVE','ENDED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE flash_sale_orders (
    id             BIGSERIAL PRIMARY KEY,
    flash_sale_id  BIGINT NOT NULL REFERENCES flash_sales(id),
    user_id        BIGINT NOT NULL REFERENCES users(id),
    order_id       BIGINT REFERENCES orders(id),
    quantity       INTEGER NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(flash_sale_id, user_id)
);

CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB NOT NULL,
    processed    BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);
