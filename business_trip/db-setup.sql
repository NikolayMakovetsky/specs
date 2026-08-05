-- Создание таблицы city_rates
CREATE TABLE IF NOT EXISTS city_rates (
    id INT PRIMARY KEY AUTO_INCREMENT,
    city_name VARCHAR(255) NOT NULL UNIQUE,
    daily_rate INT NOT NULL,
    currency VARCHAR(10) DEFAULT 'RUB',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Начальные данные
INSERT INTO city_rates (city_name, daily_rate) VALUES
('Москва', 5500),
('Санкт-Петербург', 5000),
('Казань', 4500),
('Новосибирск', 4200),
('Екатеринбург', 4000),
('Краснодар', 3800),
('Сочи', 4500),
('Владивосток', 4800),
('Ростов-на-Дону', 3700),
('Тюмень', 4100),
('Нижний Новгород', 3900),
('Самара', 3800),
('Уфа', 3700),
('Омск', 3600),
('Челябинск', 3700),
('Красноярск', 4000),
('Пермь', 3600),
('Волгоград', 3500)
ON DUPLICATE KEY UPDATE daily_rate = VALUES(daily_rate);