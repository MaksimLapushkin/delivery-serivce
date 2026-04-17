CREATE TABLE delivery (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    delivery_city VARCHAR(255) NOT NULL,
    delivery_postal_code VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    returned_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE delivery_timeline (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL
);

CREATE TABLE processed_event (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_event (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

ALTER TABLE delivery
    ADD CONSTRAINT uk_delivery_order_id UNIQUE (order_id);

ALTER TABLE delivery_timeline
    ADD CONSTRAINT uk_delivery_timeline_event_id UNIQUE (event_id);

CREATE INDEX idx_delivery_timeline_delivery_id_occurred_at
    ON delivery_timeline (delivery_id, occurred_at);
