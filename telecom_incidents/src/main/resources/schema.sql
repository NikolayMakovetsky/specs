-- Справочники
CREATE TABLE IF NOT EXISTS task_type_ref (
    id INT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS unit_role_ref (
    id INT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255)
);

-- Инциденты
CREATE TABLE IF NOT EXISTS incident (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    number VARCHAR(20) UNIQUE NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    problem_type VARCHAR(50) NOT NULL,
    priority INT DEFAULT 5,
    status VARCHAR(50) DEFAULT 'CREATED',
    macro_segment VARCHAR(10),
    segment VARCHAR(10),
    mfu_client VARCHAR(100),
    address TEXT,
    cms_order_number VARCHAR(20),
    created_date TIMESTAMP,
    updated_date TIMESTAMP
);

-- Задания
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(50) PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    task_type INT NOT NULL,
    unit_role VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'CREATED',
    result VARCHAR(255),
    parent_task_id VARCHAR(50),
    created_date TIMESTAMP,
    started_date TIMESTAMP,
    closed_date TIMESTAMP,
    FOREIGN KEY (incident_id) REFERENCES incident(id)
);

-- Правила
CREATE TABLE IF NOT EXISTS cache_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    external_id INT NOT NULL,
    process_name VARCHAR(100) NOT NULL,
    process_version INT NOT NULL,
    close_code VARCHAR(50) NOT NULL,
    target_unit_role VARCHAR(50) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rule_source_task_type_link (
    rule_id BIGINT NOT NULL,
    task_type_id INT NOT NULL,
    PRIMARY KEY (rule_id, task_type_id),
    FOREIGN KEY (rule_id) REFERENCES cache_rule(id),
    FOREIGN KEY (task_type_id) REFERENCES task_type_ref(id)
);

CREATE TABLE IF NOT EXISTS rule_source_unit_role_link (
    rule_id BIGINT NOT NULL,
    unit_role_id INT NOT NULL,
    PRIMARY KEY (rule_id, unit_role_id),
    FOREIGN KEY (rule_id) REFERENCES cache_rule(id),
    FOREIGN KEY (unit_role_id) REFERENCES unit_role_ref(id)
);

CREATE TABLE IF NOT EXISTS rule_context_conditions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    condition_key VARCHAR(50) NOT NULL,
    condition_value VARCHAR(50) NOT NULL,
    FOREIGN KEY (rule_id) REFERENCES cache_rule(id)
);

CREATE TABLE IF NOT EXISTS rule_target (
    rule_id BIGINT NOT NULL,
    target_task_type INT NOT NULL,
    PRIMARY KEY (rule_id, target_task_type),
    FOREIGN KEY (rule_id) REFERENCES cache_rule(id),
    FOREIGN KEY (target_task_type) REFERENCES task_type_ref(id)
);