-- H3: indexes on high-traffic session_id columns (not auto-created by MySQL for non-FK columns)
CREATE INDEX idx_orders_session_id     ON orders(session_id);
CREATE INDEX idx_cart_items_session_id ON cart_items(session_id);

-- H4: convert DOUBLE money columns to DECIMAL(10,2) to eliminate floating-point rounding errors
ALTER TABLE orders      MODIFY COLUMN total_amount DECIMAL(10, 2);
ALTER TABLE order_items MODIFY COLUMN price        DECIMAL(10, 2) NOT NULL;
