CREATE DATABASE saga_order_db;
CREATE DATABASE saga_inventory_db;
\c saga_inventory_db;
CREATE TABLE inventory (product_id BIGINT PRIMARY KEY, available_stock INT);
INSERT INTO inventory (product_id, available_stock) VALUES (101, 5);
COMMIT;