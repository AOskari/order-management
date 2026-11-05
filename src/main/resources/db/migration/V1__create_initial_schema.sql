CREATE TABLE "order" (
    id           UUID PRIMARY KEY,
    customer_id  UUID NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'CREATED'
                 CHECK (status IN ('CREATED', 'CONFIRMED', 'SHIPPED', 'DELIVERED')),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX order_customer_id_idx ON "order" (customer_id);
CREATE INDEX order_status_idx      ON "order" (status);
CREATE INDEX order_created_at_idx  ON "order" (created_at DESC);

CREATE TABLE order_item (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES "order" (id) ON DELETE CASCADE,
    product_id   VARCHAR(64)  NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    price_unit   VARCHAR(16)  NOT NULL
                 CHECK (price_unit IN ('PIECE', 'KILOGRAM', 'LITER')),
    unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    quantity     INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE INDEX order_item_order_id_idx ON order_item (order_id);

GRANT USAGE ON SCHEMA public TO oms_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON "order", order_item TO oms_app;
