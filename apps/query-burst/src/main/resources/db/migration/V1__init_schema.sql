-- ============================================================
-- V1: 초기 스키마 생성
-- 적재 순서: category → member → product → orders → order_item
-- ============================================================

-- ────────────── category ──────────────
CREATE TABLE category
(
    id         BIGINT       NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    parent_id  BIGINT,
    depth      INT          NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
);

CREATE INDEX idx_category_parent_id ON category (parent_id);
CREATE INDEX idx_category_depth ON category (depth);


-- ────────────── member ──────────────
CREATE TABLE member
(
    id         BIGINT       NOT NULL,
    email      VARCHAR(100) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    grade      VARCHAR(10)  NOT NULL,
    region     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_email UNIQUE (email)
);

CREATE INDEX idx_member_grade ON member (grade);
CREATE INDEX idx_member_region ON member (region);
CREATE INDEX idx_member_created_at ON member (created_at);
CREATE INDEX idx_member_grade_created_at ON member (grade, created_at);


-- ────────────── product ──────────────
CREATE TABLE product
(
    id          BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    price       BIGINT       NOT NULL,
    stock       INT          NOT NULL,
    status      VARCHAR(10)  NOT NULL,
    category_id BIGINT       NOT NULL,
    seller_id   BIGINT       NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES member (id)
);

CREATE INDEX idx_product_category_id ON product (category_id);
CREATE INDEX idx_product_seller_id ON product (seller_id);
CREATE INDEX idx_product_status ON product (status);
CREATE INDEX idx_product_price ON product (price);
CREATE INDEX idx_product_category_status_price ON product (category_id, status, price);


-- ────────────── orders ──────────────
CREATE TABLE orders
(
    id           BIGINT      NOT NULL,
    member_id    BIGINT      NOT NULL,
    status       VARCHAR(15) NOT NULL,
    total_amount BIGINT      NOT NULL,
    ordered_at   TIMESTAMP   NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE INDEX idx_orders_member_id ON orders (member_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_ordered_at ON orders (ordered_at);
CREATE INDEX idx_orders_member_ordered_at ON orders (member_id, ordered_at);
CREATE INDEX idx_orders_status_ordered_at ON orders (status, ordered_at);


-- ────────────── order_item ──────────────
CREATE TABLE order_item
(
    id         BIGINT    NOT NULL,
    order_id   BIGINT    NOT NULL,
    product_id BIGINT    NOT NULL,
    quantity   INT       NOT NULL,
    unit_price BIGINT    NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);
CREATE INDEX idx_order_item_product_id ON order_item (product_id);
CREATE INDEX idx_order_item_product_created_at ON order_item (product_id, created_at);
CREATE INDEX idx_order_item_covering ON order_item (product_id, quantity, unit_price);
