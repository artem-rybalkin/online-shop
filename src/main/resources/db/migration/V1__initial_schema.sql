-- Initial schema: all tables created by Flyway from this point forward.
-- ddl-auto is set to none in production; Flyway owns all DDL.

CREATE TABLE IF NOT EXISTS users (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email    VARCHAR(255) NOT NULL,
    role     VARCHAR(20)  NOT NULL DEFAULT 'USER',
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS products (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       DOUBLE       NOT NULL,
    stock       INT,
    category    VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_products_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS orders (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    customer_name  VARCHAR(255),
    customer_email VARCHAR(255),
    status         VARCHAR(20)  NOT NULL,
    payment_method VARCHAR(100),
    session_id     VARCHAR(255),
    total_amount   DOUBLE,
    created_at     DATETIME(6),
    user_id        BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_name VARCHAR(255),
    price        DOUBLE,
    quantity     INT,
    order_id     BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE TABLE IF NOT EXISTS cart_items (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(255),
    quantity   INT,
    product_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
