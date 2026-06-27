-- M1: add missing DB constraints for data integrity

-- Prevent negative stock
ALTER TABLE products ADD CONSTRAINT chk_products_stock CHECK (stock >= 0);

-- Enforce valid order status values
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'));

-- Order items must always belong to an order
ALTER TABLE order_items MODIFY COLUMN order_id BIGINT NOT NULL;

-- Order items must have positive quantity
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_quantity CHECK (quantity > 0);
