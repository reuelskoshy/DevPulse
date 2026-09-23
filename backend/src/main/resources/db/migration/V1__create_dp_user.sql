CREATE TABLE dp_user (
    id CHAR(36) PRIMARY KEY,
    active_status BOOLEAN NOT NULL DEFAULT TRUE,
    active_status_reason VARCHAR(500),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    phone_number VARCHAR(20),
    address TEXT,
    password VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    parent CHAR(36),
    role VARCHAR(50) NOT NULL,
    account_created_datetime DATETIME(6) NOT NULL,
    account_modified_datetime DATETIME(6) NOT NULL,
    last_password_reset DATETIME(6),
    mfa_active BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_dp_user_email UNIQUE (email),
    CONSTRAINT fk_dp_user_parent FOREIGN KEY (parent)
        REFERENCES dp_user(id) ON DELETE SET NULL,
    INDEX idx_dp_user_email (email),
    INDEX idx_dp_user_parent (parent),
    INDEX idx_dp_user_role (role),
    INDEX idx_dp_user_active_status (active_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
