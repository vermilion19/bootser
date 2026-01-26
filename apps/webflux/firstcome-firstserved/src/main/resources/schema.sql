-- H2 문법에 맞춰 작성
CREATE TABLE IF NOT EXISTS item (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    name VARCHAR(255),
                                    price DECIMAL(10, 2),
                                    stock INT
);

CREATE TABLE IF NOT EXISTS orders (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      user_id BIGINT,
                                      item_id BIGINT,
                                      status VARCHAR(50),
                                      created_at TIMESTAMP
);

-- 테스트용 초기 데이터 (선택)
INSERT INTO item (name, price, stock) VALUES ('한정판 운동화', 150000, 100);