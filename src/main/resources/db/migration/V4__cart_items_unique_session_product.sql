-- M3: prevent duplicate cart rows for the same session+product under concurrent adds
ALTER TABLE cart_items ADD CONSTRAINT uk_cart_items_session_product UNIQUE (session_id, product_id);
