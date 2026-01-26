-- H2 문법에 맞춰 작성
CREATE TABLE IF NOT EXISTS "item" (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    name VARCHAR(255),
                                    price DECIMAL(10, 2),
                                    stock INT
);

CREATE TABLE IF NOT EXISTS "product_order" (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      order_id VARCHAR(36),
                                      user_id BIGINT,
                                      item_id BIGINT,
                                      quantity INT DEFAULT 1,
                                      status VARCHAR(50),
                                      created_at TIMESTAMP
);

-- 테스트용 초기 데이터 (선택)
INSERT INTO "item" (name, price, stock) VALUES ('한정판 운동화', 150000, 100);